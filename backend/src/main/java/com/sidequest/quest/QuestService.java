package com.sidequest.quest;

import com.sidequest.identity.UserRepository;
import com.sidequest.moderation.ModerationState;
import com.sidequest.moderation.AccountAbuseQuota;
import com.sidequest.moderation.QuestModeration;
import com.sidequest.moderation.QuestModerationRepository;
import com.sidequest.moderation.QuestSafetyScreen;
import java.time.Clock;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class QuestService {
    private final QuestRepository quests;
    private final UserRepository users;
    private final QuestModerationRepository moderation;
    private final QuestSafetyScreen safety;
    private final Clock clock;
    private final AccountAbuseQuota abuseQuota;

    QuestService(QuestRepository quests, UserRepository users, QuestModerationRepository moderation,
                 QuestSafetyScreen safety, Clock clock, AccountAbuseQuota abuseQuota) {
        this.quests = quests;
        this.users = users;
        this.moderation = moderation;
        this.safety = safety;
        this.clock = clock;
        this.abuseQuota = abuseQuota;
    }

    @Transactional
    VersionedQuest create(UUID creatorId, QuestDtos.CreateDraft request) {
        QuestDraftValidator.DraftValues values = QuestDraftValidator.create(request);
        var creator = users.findById(creatorId).orElseThrow(QuestApiFailure::notFound);
        abuseQuota.requireQuestCreation(creatorId);
        Quest quest = Quest.create(creator, values.title(), values.description(),
                values.category(), values.difficulty(), values.rewardXp());
        quest = quests.saveAndFlush(quest);
        moderation.save(QuestModeration.unreviewed(quest));
        return versioned(quest);
    }

    @Transactional(readOnly = true)
    Page<Quest> ownerPage(UUID creatorId, QuestStatus status, int page, int size) {
        var pageable = pageable(page, size);
        return status == null
                ? quests.findAllByCreator_Id(creatorId, pageable)
                : quests.findAllByCreator_IdAndStatus(creatorId, status, pageable);
    }

    @Transactional(readOnly = true)
    Page<Quest> publicPage(
            String query, QuestCategory category, QuestDifficulty difficulty, int page, int size) {
        return quests.searchPublished(normalizeSearchQuery(query), category, difficulty, pageable(page, size));
    }

    @Transactional(readOnly = true)
    VersionedQuest ownerQuest(UUID creatorId, UUID id) {
        return versioned(ownedQuest(creatorId, id));
    }

    @Transactional(readOnly = true)
    VersionedQuest publicQuest(UUID readerId, UUID id) {
        Quest quest = quests.findApprovedPublished(id, QuestStatus.PUBLISHED, ModerationState.APPROVED)
                .orElseGet(() -> readerId == null ? null
                        : quests.findByIdAndCreator_Id(id, readerId).orElse(null));
        if (quest == null) throw QuestApiFailure.notFound();
        return versioned(quest);
    }

    @Transactional
    VersionedQuest patch(UUID creatorId, UUID id, String ifMatch, QuestDtos.PatchDraft request) {
        Quest quest = quests.findByIdAndCreatorForUpdate(id, creatorId).orElseThrow(QuestApiFailure::notFound);
        if (quest.getStatus() != QuestStatus.DRAFT) throw QuestApiFailure.conflict();
        requireCurrentVersion(ifMatch, quest.getVersion());
        QuestDraftValidator.DraftValues values = QuestDraftValidator.patch(quest, request);
        if (moderation.findById(id).map(m -> m.getState() == ModerationState.UNDER_REVIEW).orElse(false)) {
            throw QuestApiFailure.conflict();
        }
        quest.updateDraft(values.title(), values.description(), values.category(),
                values.difficulty(), values.rewardXp());
        moderation.findById(quest.getId()).ifPresent(QuestModeration::invalidateReview);
        quests.flush();
        return versioned(quest);
    }

    @Transactional
    VersionedQuest publish(UUID creatorId, UUID id, String ifMatch) {
        Quest quest = quests.findByIdAndCreatorForUpdate(id, creatorId).orElseThrow(QuestApiFailure::notFound);
        long suppliedVersion = parseIfMatch(ifMatch);
        if (quest.getStatus() == QuestStatus.PUBLISHED) return versioned(quest);
        if (quest.getStatus() != QuestStatus.DRAFT) throw QuestApiFailure.conflict();
        requireCurrentVersion(suppliedVersion, quest.getVersion());
        var errors = QuestDraftValidator.publicationErrors(quest);
        if (!errors.isEmpty()) throw QuestApiFailure.validation(errors);
        if (!moderation.findById(id).map(QuestModeration::approved).orElse(false)) {
            throw QuestApiFailure.reviewRequired();
        }
        quest.publish();
        quests.flush();
        return versioned(quest);
    }

    /** Submit complete draft to private review. Screening is a triage signal, not approval. */
    @Transactional
    ModerationSubmission submitForReview(UUID creatorId, UUID id, String ifMatch) {
        Quest quest = quests.findByIdAndCreatorForUpdate(id, creatorId).orElseThrow(QuestApiFailure::notFound);
        long supplied = parseIfMatch(ifMatch);
        if (quest.getStatus() != QuestStatus.DRAFT) throw QuestApiFailure.conflict();
        requireCurrentVersion(supplied, quest.getVersion());
        var errors = QuestDraftValidator.publicationErrors(quest);
        if (!errors.isEmpty()) throw QuestApiFailure.validation(errors);
        QuestModeration review = moderation.findById(id).orElseGet(() -> QuestModeration.unreviewed(quest));
        if (review.getState() != ModerationState.PENDING_REVIEW) {
            if (review.approved()) throw QuestApiFailure.conflict();
            review.submit(safety.assess(quest.getTitle(), quest.getDescription()), clock.instant());
            moderation.saveAndFlush(review);
        }
        return new ModerationSubmission(review.getState().name(), review.getReasonCode(), review.getVersion());
    }

    @Transactional(readOnly = true)
    ModerationSubmission reviewStatus(UUID creatorId, UUID id) {
        ownedQuest(creatorId, id); // Conceal non-owned resources.
        return moderation.findById(id)
                .map(m -> new ModerationSubmission(m.getState().name(), m.getReasonCode(), m.getVersion()))
                .orElseGet(() -> new ModerationSubmission(ModerationState.UNREVIEWED.name(), null, 0));
    }

    record ModerationSubmission(String state, String reasonCode, long version) { }

    @Transactional
    VersionedQuest archive(UUID creatorId, UUID id, String ifMatch) {
        Quest quest = quests.findByIdAndCreatorForUpdate(id, creatorId).orElseThrow(QuestApiFailure::notFound);
        long suppliedVersion = parseIfMatch(ifMatch);
        if (quest.getStatus() == QuestStatus.ARCHIVED) return versioned(quest);
        if (quest.getStatus() != QuestStatus.DRAFT && quest.getStatus() != QuestStatus.PUBLISHED) {
            throw QuestApiFailure.conflict();
        }
        requireCurrentVersion(suppliedVersion, quest.getVersion());
        quest.archive();
        quests.flush();
        return versioned(quest);
    }

    @Transactional(readOnly = true)
    Optional<String> currentEtag(UUID creatorId, UUID id) {
        return quests.findVersionByIdAndCreator_Id(id, creatorId).map(QuestService::etag);
    }

    private Quest ownedQuest(UUID creatorId, UUID id) {
        return quests.findByIdAndCreator_Id(id, creatorId).orElseThrow(QuestApiFailure::notFound);
    }

    private static org.springframework.data.domain.Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw QuestApiFailure.badRequest("invalid_pagination",
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw QuestApiFailure.badRequest("invalid_pagination", "The requested page is out of range.");
        }
        return PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private static String normalizeSearchQuery(String query) {
        if (query == null) return null;
        String normalized = query.strip();
        if (normalized.isEmpty()) return null;
        if (normalized.codePointCount(0, normalized.length()) > 120) {
            throw QuestApiFailure.badRequest("query_too_long",
                    "Search queries must contain at most 120 Unicode code points.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static void requireCurrentVersion(String ifMatch, long version) {
        requireCurrentVersion(parseIfMatch(ifMatch), version);
    }

    private static void requireCurrentVersion(long supplied, long current) {
        if (supplied != current) throw QuestApiFailure.preconditionFailed(etag(current));
    }

    private static long parseIfMatch(String ifMatch) {
        if (ifMatch == null) throw QuestApiFailure.preconditionRequired();
        if (!ifMatch.matches("\"(0|[1-9][0-9]*)\"")) {
            throw QuestApiFailure.badRequest("invalid_if_match",
                    "If-Match must contain one strong quest ETag.");
        }
        long supplied;
        try {
            supplied = Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw QuestApiFailure.badRequest("invalid_if_match",
                    "If-Match must contain one strong quest ETag.");
        }
        return supplied;
    }

    private static VersionedQuest versioned(Quest quest) {
        return new VersionedQuest(QuestDtos.QuestView.from(quest), etag(quest.getVersion()));
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    record VersionedQuest(QuestDtos.QuestView body, String etag) { }
}
