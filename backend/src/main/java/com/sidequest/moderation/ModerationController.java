package com.sidequest.moderation;

import com.sidequest.identity.security.UserPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
class ModerationController {
    private final ModerationOperations service;

    ModerationController(ModerationOperations service) { this.service = service; }

    record DecisionRequest(ModerationOperations.Action action, String reasonCode, Long expectedVersion) { }

    record AppealRequest(long expectedVersion, String reason) { }

    @PostMapping("/api/v1/me/quests/{id}/appeals")
    ModerationOperations.AppealResult appeal(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID id, @RequestBody AppealRequest body) {
        if (body == null) throw ModerationApiFailure.invalid();
        return service.appeal(principal.userId(), id, body.expectedVersion(), body.reason());
    }

    @GetMapping("/api/v1/moderation/legacy")
    ModerationOperations.LegacyPage legacy(@AuthenticationPrincipal UserPrincipal principal,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.legacyInventory(principal.userId(), page, size);
    }

    @PostMapping("/api/v1/moderation/legacy/{id}/intake")
    ModerationOperations.ReviewItem intakeLegacy(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID id) {
        return service.intakeLegacy(principal.userId(), id);
    }

    @GetMapping("/api/v1/moderation/queue")
    ModerationOperations.ReviewPage queue(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.queue(principal.userId(), page, size);
    }

    @PostMapping("/api/v1/moderation/quests/{id}/decisions")
    ModerationOperations.ReviewItem decide(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID id, @RequestBody DecisionRequest body) {
        if (body == null || body.action() == null || body.expectedVersion() == null)
            throw ModerationApiFailure.invalid();
        return service.decide(principal.userId(), id, body.action(), body.reasonCode(), body.expectedVersion());
    }
}
