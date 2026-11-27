package com.sidequest.team;

import com.sidequest.identity.security.UserPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TeamController {
    private final TeamService teams;
    public TeamController(TeamService teams){this.teams=teams;}
    record NewTeam(String name){}
    record InviteUser(String username){}
    record ChallengeInput(UUID questId, Instant startsAt, Instant endsAt){}
    record Version(Long expectedVersion){}

    @PostMapping("/me/teams")
    ResponseEntity<TeamService.Team> create(@AuthenticationPrincipal UserPrincipal me,@RequestBody NewTeam form){
        if(form==null)throw new TeamService.Failure(400,"invalid_team_request","Missing team.");
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(teams.create(me.userId(),form.name()));
    }
    @GetMapping("/me/teams")
    ResponseEntity<TeamService.Page<TeamService.Team>> mine(@AuthenticationPrincipal UserPrincipal me,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(teams.mine(me.userId(),page,size));
    }
    @GetMapping("/teams/{team}")
    ResponseEntity<TeamService.Team> detail(@AuthenticationPrincipal UserPrincipal me,@PathVariable UUID team){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(teams.detail(me.userId(),team));
    }
    @GetMapping("/teams/{team}/members")
    ResponseEntity<TeamService.Page<String>> members(@AuthenticationPrincipal UserPrincipal me,@PathVariable UUID team,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(teams.roster(me.userId(),team,page,size));
    }
    @PostMapping("/teams/{team}/invites")
    ResponseEntity<TeamService.Invite> invite(@AuthenticationPrincipal UserPrincipal me,
        @PathVariable UUID team,@RequestBody InviteUser form){
        if(form==null)throw new TeamService.Failure(400,"invalid_team_request","Missing invitee.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(teams.invite(me.userId(),team,form.username()));
    }
    @PostMapping("/teams/{team}/invites/{inviteId}/cancel")
    ResponseEntity<Void> cancel(@AuthenticationPrincipal UserPrincipal me,
        @PathVariable UUID team,@PathVariable UUID inviteId,@RequestBody Version body){
        if(body==null||body.expectedVersion()==null)
            throw new TeamService.Failure(400,"invalid_team_request","Missing invitation version.");
        teams.cancelInvite(me.userId(),team,inviteId,body.expectedVersion());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me/team-invites")
    ResponseEntity<TeamService.Page<TeamService.Invite>> invites(@AuthenticationPrincipal UserPrincipal me,
       @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
       return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(teams.incoming(me.userId(),page,size));
    }
    @PostMapping("/me/team-invites/{id}/{action}")
    ResponseEntity<TeamService.Invite> decide(@AuthenticationPrincipal UserPrincipal me,
        @PathVariable UUID id,@PathVariable String action,@RequestBody Version body){
        if(body==null||body.expectedVersion()==null||body.expectedVersion()<0||
            (!action.equals("accept")&&!action.equals("decline")))
            throw new TeamService.Failure(400,"invalid_team_request","Invalid decision.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(teams.respond(me.userId(),id,action.equals("accept"),body.expectedVersion()));
    }
    @DeleteMapping("/teams/{team}/members/{username}")
    ResponseEntity<Void> remove(@AuthenticationPrincipal UserPrincipal me,
        @PathVariable UUID team,@PathVariable String username){
        teams.leaveOrRemove(me.userId(),team,username);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/teams/{team}/challenges")
    ResponseEntity<TeamService.Challenge> challenge(@AuthenticationPrincipal UserPrincipal me,
        @PathVariable UUID team,@RequestBody ChallengeInput body){
        if(body==null||body.questId()==null)
            throw new TeamService.Failure(400,"invalid_team_request","Invalid challenge.");
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(teams.createChallenge(me.userId(),team,body.questId(),body.startsAt(),body.endsAt()));
    }
    @GetMapping("/teams/{team}/challenges")
    ResponseEntity<TeamService.Page<TeamService.Challenge>> challenges(@AuthenticationPrincipal UserPrincipal me,
        @PathVariable UUID team,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(teams.challenges(me.userId(),team,page,size));
    }
    @GetMapping("/teams/{team}/leaderboard")
    ResponseEntity<TeamService.Page<TeamService.Score>> scores(@AuthenticationPrincipal UserPrincipal me,
        @PathVariable UUID team,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(teams.scores(me.userId(),team,page,size));
    }
}
