package com.sidequest.group;

import com.sidequest.identity.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class GroupController {
    private final GroupService service;
    public GroupController(GroupService service) { this.service=service; }

    record CreateTeam(String name) { }
    record Invite(String username) { }
    record Version(Long expectedVersion) { }
    record NewChallenge(UUID questId,Integer days,Integer targetCompletions) { }
    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
    @PostMapping("/teams")
    ResponseEntity<GroupService.Team> create(@AuthenticationPrincipal UserPrincipal user,
            @RequestBody CreateTeam request) {
        if(request==null)throw GroupFailure.bad();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.create(user.userId(),request.name()));
    }
    @GetMapping("/me/teams")
    ResponseEntity<GroupService.Page<GroupService.Team>> mine(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(service.myTeams(user.userId(),page,size));
    }
    @GetMapping("/teams/{id}")
    ResponseEntity<GroupService.Team> team(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id) {
        return ok(service.detail(id,user.userId()));
    }
    @GetMapping("/teams/{id}/members")
    ResponseEntity<GroupService.Page<GroupService.Member>> members(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID id,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ok(service.roster(id,user.userId(),page,size));
    }
    @PostMapping("/teams/{id}/invitations")
    ResponseEntity<GroupService.Invite> invite(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,
            @RequestBody Invite request) {
        if(request==null)throw GroupFailure.bad();
        return ok(service.invite(id,user.userId(),request.username()));
    }
    @GetMapping("/teams/{id}/invitations")
    ResponseEntity<GroupService.Page<GroupService.Invite>> teamInvitations(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID id,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ok(service.teamInvites(id,user.userId(),page,size));
    }
    @GetMapping("/me/team-invitations")
    ResponseEntity<GroupService.Page<GroupService.Invite>> invitations(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(service.invitations(user.userId(),page,size));
    }
    @PostMapping("/me/team-invitations/{id}/{action}")
    ResponseEntity<GroupService.Invite> decide(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,
            @PathVariable String action,@RequestBody Version request) {
        if(request==null||request.expectedVersion()==null)throw GroupFailure.bad();
        return ok(service.decide(user.userId(),id,action,request.expectedVersion()));
    }
    @PostMapping("/teams/{id}/leave")
    ResponseEntity<GroupService.Team> leave(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id) {
        return ok(service.leave(id,user.userId()));
    }
    @PostMapping("/teams/{id}/members/{memberId}/remove")
    ResponseEntity<Void> remove(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,
            @PathVariable UUID memberId) {
        service.remove(id,user.userId(),memberId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/teams/{id}/archive")
    ResponseEntity<GroupService.Team> archive(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id) {
        return ok(service.archive(id,user.userId()));
    }
    @PostMapping("/teams/{id}/challenges")
    ResponseEntity<GroupService.Challenge> challenge(@AuthenticationPrincipal UserPrincipal user,@PathVariable UUID id,
            @RequestBody NewChallenge request) {
        if(request==null||request.days()==null||request.targetCompletions()==null)throw GroupFailure.bad();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.challenge(id,user.userId(),request.questId(),request.days(),request.targetCompletions()));
    }
    @GetMapping("/teams/{id}/challenges")
    ResponseEntity<GroupService.Page<GroupService.Challenge>> challenges(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(service.challenges(id,user.userId(),page,size));
    }
    @GetMapping("/teams/{id}/leaderboard")
    ResponseEntity<GroupService.Page<GroupService.Score>> leaderboard(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(service.leaderboard(id,user.userId(),page,size));
    }
}
