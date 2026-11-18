package com.sidequest.quest;

import com.sidequest.identity.security.UserPrincipal;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuestController {
    private final QuestService service;

    QuestController(QuestService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/quests")
    ResponseEntity<QuestDtos.QuestView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody QuestDtos.CreateDraft request) {
        QuestService.VersionedQuest created = service.create(principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/quests/" + created.body().id()))
                .eTag(created.etag())
                .body(created.body());
    }

    @PatchMapping("/api/v1/quests/{id}")
    ResponseEntity<QuestDtos.QuestView> patch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody QuestDtos.PatchDraft request) {
        QuestService.VersionedQuest updated = service.patch(principal.userId(), id, ifMatch, request);
        return ResponseEntity.ok().eTag(updated.etag()).body(updated.body());
    }

    @GetMapping("/api/v1/me/quests")
    QuestDtos.QuestPage listOwned(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) QuestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Quest> quests = service.ownerPage(principal.userId(), status, page, size);
        return QuestDtos.QuestPage.from(quests);
    }

    @GetMapping("/api/v1/me/quests/{id}")
    ResponseEntity<QuestDtos.QuestView> getOwned(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        QuestService.VersionedQuest quest = service.ownerQuest(principal.userId(), id);
        return ResponseEntity.ok().eTag(quest.etag()).body(quest.body());
    }

    @PostMapping("/api/v1/quests/{id}/publish")
    ResponseEntity<QuestDtos.QuestView> publish(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        QuestService.VersionedQuest quest = service.publish(principal.userId(), id, ifMatch);
        return ResponseEntity.ok().eTag(quest.etag()).body(quest.body());
    }

    @PostMapping("/api/v1/quests/{id}/archive")
    ResponseEntity<QuestDtos.QuestView> archive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        QuestService.VersionedQuest quest = service.archive(principal.userId(), id, ifMatch);
        return ResponseEntity.ok().eTag(quest.etag()).body(quest.body());
    }
}
