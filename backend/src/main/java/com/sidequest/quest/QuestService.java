package com.sidequest.quest;

import com.sidequest.identity.UserRepository;
import java.util.Optional;
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

    QuestService(QuestRepository quests, UserRepository users) {
        this.quests = quests;
        this.users = users;
    }

    @Transactional
    VersionedQuest create(UUID creatorId, QuestDtos.CreateDraft request) {
        QuestDraftValidator.DraftValues values = QuestDraftValidator.create(request);
        var creator = users.findById(creatorId).orElseThrow(QuestApiFailure::notFound);
        Quest quest = Quest.create(creator, values.title(), values.description(),
                values.category(), values.difficulty(), values.rewardXp());
        quests.saveAndFlush(quest);
        return versioned(quest);
    }

    @Transactional(readOnly = true)
    Page<Quest> ownerPage(UUID creatorId, QuestStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw QuestApiFailure.badRequest("invalid_pagination",
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return status == null
                ? quests.findAllByCreator_Id(creatorId, pageable)
                : quests.findAllByCreator_IdAndStatus(creatorId, status, pageable);
    }

    @Transactional(readOnly = true)
    VersionedQuest ownerQuest(UUID creatorId, UUID id) {
        return versioned(ownedQuest(creatorId, id));
    }

    @Transactional
    VersionedQuest patch(UUID creatorId, UUID id, String ifMatch, QuestDtos.PatchDraft request) {
        Quest quest = ownedQuest(creatorId, id);
        if (quest.getStatus() != QuestStatus.DRAFT) throw QuestApiFailure.conflict();
        requireCurrentVersion(ifMatch, quest.getVersion());
        QuestDraftValidator.DraftValues values = QuestDraftValidator.patch(quest, request);
        quest.updateDraft(values.title(), values.description(), values.category(),
                values.difficulty(), values.rewardXp());
        quests.flush();
        return versioned(quest);
    }

    @Transactional
    VersionedQuest publish(UUID creatorId, UUID id, String ifMatch) {
        Quest quest = ownedQuest(creatorId, id);
        long suppliedVersion = parseIfMatch(ifMatch);
        if (quest.getStatus() == QuestStatus.PUBLISHED) return versioned(quest);
        if (quest.getStatus() != QuestStatus.DRAFT) throw QuestApiFailure.conflict();
        requireCurrentVersion(suppliedVersion, quest.getVersion());
        var errors = QuestDraftValidator.publicationErrors(quest);
        if (!errors.isEmpty()) throw QuestApiFailure.validation(errors);
        quest.publish();
        quests.flush();
        return versioned(quest);
    }

    @Transactional
    VersionedQuest archive(UUID creatorId, UUID id, String ifMatch) {
        Quest quest = ownedQuest(creatorId, id);
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
