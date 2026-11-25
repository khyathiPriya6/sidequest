package com.sidequest.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class QuestSafetyScreenTest {
    private final QuestSafetyScreen screen = new QuestSafetyScreen();

    @Test
    void safeWalkingChallengeHasNoDeterministicSignalsButIsNotAutoApproved() {
        var result = screen.assess("Morning walking Quest", "Walk five thousand steps and enjoy your local park.");
        assertThat(result.risk()).isEqualTo(QuestSafetyScreen.Risk.NO_SIGNAL);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void recklessRoadRaceGetsHighPriorityHumanReview() {
        var result = screen.assess("Race the highway", "Drive as fast as you can in traffic today.");
        assertThat(result.risk()).isEqualTo(QuestSafetyScreen.Risk.HIGH_PRIORITY_REVIEW);
        assertThat(result.reasons()).contains(QuestSafetyScreen.Reason.PHYSICAL_DANGER);
    }

    @Test
    void exposingPrivateDataGetsHighPriorityHumanReview() {
        var result = screen.assess("Share secrets", "Post someone else's home address to get points.");
        assertThat(result.reasons()).contains(QuestSafetyScreen.Reason.PRIVACY);
    }

    @Test
    void vagueClimbingTaskGetsManualReview() {
        assertThat(screen.assess("Climb something tall", "Climb the tallest structure near your home.")
                .risk()).isEqualTo(QuestSafetyScreen.Risk.REVIEW);
    }

    @Test
    void noKeywordMatchIsNotProofThatChallengeIsSafe() {
        assertThat(screen.assess("Something vague", "Do a very risky activity with your friends.")
                .risk()).isEqualTo(QuestSafetyScreen.Risk.NO_SIGNAL);
    }

    @Test
    void educationalContextCanBeOverFlaggedSoReviewIsRequiredInsteadOfPunishment() {
        assertThat(screen.assess("Learn road safety", "Discuss how driving at high speed on the highway creates danger.")
                .risk()).isEqualTo(QuestSafetyScreen.Risk.HIGH_PRIORITY_REVIEW);
    }
}
