package com.sidequest.participation;

import java.time.Instant;
import java.util.List;

final class AchievementCatalog {
    private AchievementCatalog() { }
    static List<ProgressionDtos.Achievement> build(ProgressionReadRepository.Summary s,
            ProgressionReadRepository.Events e) {
        if (s.totalXp() < 0 || s.integrityErrors() != 0) throw ParticipationFailure.integrity();
        return List.of(
            item("first_adventure","First Adventure","Complete any Quest.",s.completed(),1,e.firstQuest()),
            item("quest_explorer","Quest Explorer","Complete 5 Quests.",s.completed(),5,e.fifthQuest()),
            item("quest_veteran","Quest Veteran","Complete 10 Quests.",s.completed(),10,e.tenthQuest()),
            item("dedicated_adventurer","Dedicated Adventurer","Complete 25 Quests.",s.completed(),25,e.twentyFifthQuest()),
            item("xp_rookie","XP Rookie","Earn 500 XP.",s.totalXp(),500,e.xp500()),
            item("rising_hero","Rising Hero","Earn 1000 XP.",s.totalXp(),1000,e.xp1000()),
            item("quest_master","Quest Master","Earn 5000 XP.",s.totalXp(),5000,e.xp5000()),
            item("all_rounder","All-Rounder","Complete a Quest in each of the six categories.",s.categories(),6,e.allCategories()),
            item("challenge_seeker","Challenge Seeker","Complete 5 HARD Quests.",s.hard(),5,e.fifthHard()));
    }
    private static ProgressionDtos.Achievement item(String id,String name,String description,long progress,long target,Instant unlockedAt) {
        long bounded=Math.min(progress,target);
        boolean unlocked=bounded>=target;
        if (unlocked != (unlockedAt != null)) throw ParticipationFailure.integrity();
        return new ProgressionDtos.Achievement(id,name,description,bounded,target,unlocked,unlockedAt);
    }
}
