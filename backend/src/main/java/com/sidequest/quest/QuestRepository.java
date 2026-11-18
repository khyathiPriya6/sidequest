package com.sidequest.quest;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestRepository extends JpaRepository<Quest, UUID> { }
