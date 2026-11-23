package com.sidequest.moderation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** No public access paths. SAFETY-03 must enforce visibility at every read and Join. */
public interface QuestModerationRepository extends JpaRepository<QuestModeration, UUID> {
}
