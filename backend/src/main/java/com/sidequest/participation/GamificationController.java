package com.sidequest.participation;

import com.sidequest.identity.security.UserPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class GamificationController {
    private final LeaderboardService leaderboard;
    public GamificationController(LeaderboardService leaderboard) { this.leaderboard = leaderboard; }

    @GetMapping("/api/v1/leaderboard")
    ResponseEntity<LeaderboardService.Board> board(@RequestParam(defaultValue="all") String period,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leaderboard.board(period, page, size));
    }

    @GetMapping("/api/v1/me/leaderboard-rank")
    ResponseEntity<LeaderboardService.Ranking> rank(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue="all") String period) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leaderboard.rank(principal.userId(), period));
    }
}
