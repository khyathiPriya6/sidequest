package com.sidequest.participation;

import com.sidequest.identity.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ParticipationController {
    private final ParticipationService service;
    ParticipationController(ParticipationService service) { this.service = service; }

    @PostMapping("/api/v1/quests/{id}/join")
    ResponseEntity<ParticipationDtos.View> join(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id, HttpServletRequest request) throws IOException {
        requireNoBody(request);
        var result = service.join(principal.userId(), uuid(id));
        var response = result.created()
                ? ResponseEntity.created(URI.create("/api/v1/me/participations/" + result.body().id()))
                : ResponseEntity.ok();
        return response.cacheControl(CacheControl.noStore()).body(result.body());
    }

    @PostMapping("/api/v1/participations/{id}/complete")
    ResponseEntity<ParticipationDtos.View> complete(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id, HttpServletRequest request) throws IOException {
        requireNoBody(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.complete(principal.userId(), uuid(id)));
    }

    @GetMapping("/api/v1/me/participations")
    ResponseEntity<ParticipationDtos.HistoryPage> history(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status, @RequestParam(required = false) String questId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        ParticipationStatus filter = null;
        if (status != null) {
            try { filter = ParticipationStatus.valueOf(status); }
            catch (IllegalArgumentException error) { throw ParticipationFailure.badRequest("invalid_parameter"); }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.history(principal.userId(), filter, questId == null ? null : uuid(questId), page, size));
    }

    @GetMapping("/api/v1/me/participations/{id}")
    ResponseEntity<ParticipationDtos.View> detail(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detail(principal.userId(), uuid(id)));
    }

    static UUID uuid(String value) {
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw ParticipationFailure.badRequest("invalid_parameter");
        }
        return UUID.fromString(value);
    }

    static void requireNoBody(HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) throw ParticipationFailure.badRequest("unexpected_body");
    }
}
