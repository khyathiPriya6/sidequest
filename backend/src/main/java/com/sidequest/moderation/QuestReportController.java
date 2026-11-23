package com.sidequest.moderation;

import com.sidequest.identity.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
class QuestReportController {
    private final ModerationOperations service;

    QuestReportController(ModerationOperations service) { this.service = service; }

    record ReportRequest(ModerationOperations.ReportReason reason) { }
    record ReportReceipt(UUID id, String status) { }

    @PostMapping("/api/v1/quests/{id}/reports")
    ResponseEntity<ReportReceipt> report(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID id, @RequestBody ReportRequest body) {
        if (body == null || body.reason() == null) throw ModerationApiFailure.invalid();
        var result = service.report(principal.userId(), id, body.reason());
        var receipt = new ReportReceipt(result.id(), "RECEIVED");
        return result.created() ? ResponseEntity.status(201).body(receipt) : ResponseEntity.ok(receipt);
    }
}
