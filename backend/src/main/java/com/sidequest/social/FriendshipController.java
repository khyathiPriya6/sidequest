package com.sidequest.social;

import com.sidequest.identity.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class FriendshipController {
    private final FriendshipService service;
    public FriendshipController(FriendshipService service) {this.service=service;}
    record Invite(String username) { }
    record Decision(Long expectedVersion) { }

    @PostMapping("/friend-requests")
    ResponseEntity<FriendshipService.Connection> invite(@AuthenticationPrincipal UserPrincipal user,@RequestBody Invite input) {
        if(input==null) throw SocialProfileFailure.invalid();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.send(user.userId(),input.username()));
    }
    @GetMapping("/friend-requests")
    ResponseEntity<FriendshipService.Connections> requests(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="incoming") String direction,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.requests(user.userId(),direction,page,size));
    }
    @PostMapping("/friend-requests/{id}/{action}")
    ResponseEntity<FriendshipService.Connection> decide(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID id,@PathVariable String action,@RequestBody Decision input) {
        if(input==null) throw SocialProfileFailure.invalid();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.decide(user.userId(),id,action,input.expectedVersion()));
    }
    @GetMapping("/friends")
    ResponseEntity<FriendshipService.Connections> friends(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.friends(user.userId(),page,size));
    }
    record Peer(String username) { }
    @PostMapping("/social/blocks")
    ResponseEntity<Void> block(@AuthenticationPrincipal UserPrincipal user,@RequestBody Peer input) {
        if(input==null) throw SocialProfileFailure.invalid();
        service.block(user.userId(),input.username(),true);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/social/unblocks")
    ResponseEntity<Void> unblock(@AuthenticationPrincipal UserPrincipal user,@RequestBody Peer input) {
        if(input==null) throw SocialProfileFailure.invalid();
        service.block(user.userId(),input.username(),false);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/friends/leaderboard")
    ResponseEntity<FriendshipService.FriendBoard> board(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="all") String period,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.leaderboard(user.userId(),period,page,size));
    }
}
