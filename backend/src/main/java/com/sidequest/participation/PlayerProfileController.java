package com.sidequest.participation;

import com.sidequest.identity.security.UserPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlayerProfileController {
    private final PlayerProfileService service;
    public PlayerProfileController(PlayerProfileService service){this.service=service;}

    @GetMapping("/api/v1/me/profile")
    ResponseEntity<ProgressionDtos.Profile> profile(@AuthenticationPrincipal UserPrincipal principal){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.profile(principal.userId()));
    }
    @GetMapping("/api/v1/me/achievements")
    ResponseEntity<ProgressionDtos.AchievementCatalog> achievements(@AuthenticationPrincipal UserPrincipal principal){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.achievements(principal.userId()));
    }
    @GetMapping("/api/v1/me/xp-transactions")
    ResponseEntity<ProgressionDtos.XpPage> transactions(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.transactions(principal.userId(),page,size));
    }
}
