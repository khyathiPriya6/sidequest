package com.sidequest.participation;

import com.sidequest.identity.security.UserPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class GamificationController {
    private final LeaderboardService leaderboard;
    private final StreakService streaks;
    public GamificationController(LeaderboardService leaderboard, StreakService streaks) {
        this.leaderboard = leaderboard; this.streaks = streaks;
    }

    @GetMapping("/api/v1/me/streak")
    ResponseEntity<StreakService.Streak> streak(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(streaks.streak(principal.userId()));
    }

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
