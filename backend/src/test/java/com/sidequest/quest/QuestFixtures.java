package com.sidequest.quest;

public final class QuestFixtures {
    private QuestFixtures() { }

    public static Quest published(Quest quest) {
        quest.publish();
        return quest;
    }

    public static Quest archived(Quest quest) {
        quest.archive();
        return quest;
    }
}
