package com.sidequest.social;

import com.sidequest.identity.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SocialProfileController {
    private final SocialProfileService service;

    public SocialProfileController(SocialProfileService service) {
        this.service = service;
    }

    record VisibilityChange(String visibility, Long expectedVersion) { }

    @GetMapping("/me/social/settings")
    ResponseEntity<SocialProfileService.Preferences> settings(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.settings(user.userId()));
    }

    @PatchMapping("/me/social/settings")
    ResponseEntity<SocialProfileService.Preferences> change(@AuthenticationPrincipal UserPrincipal user,
            @RequestBody VisibilityChange request) {
        if (request == null) throw SocialProfileFailure.invalid();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.update(user.userId(), request.visibility(), request.expectedVersion()));
    }

    @GetMapping("/social/profiles/{profileId}")
    ResponseEntity<SocialProfileService.Profile> profile(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID profileId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.profile(user.userId(), profileId));
    }
}
