--liquibase formatted sql
--changeset sidequest:008-quest-moderation
-- This creates review metadata only; SAFETY-03 separately enforces public visibility.
-- Legacy quests have no row until explicit backfill/review. Never imply approval.
CREATE TABLE quest_moderation (
    quest_id UUID PRIMARY KEY,
    state VARCHAR(24) NOT NULL DEFAULT 'UNREVIEWED',
    policy_version VARCHAR(32) NOT NULL DEFAULT 'm3-v1',
    reason_code VARCHAR(40),
    submitted_at TIMESTAMP WITH TIME ZONE,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT quest_moderation_quest_fk
        FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE RESTRICT,
    CONSTRAINT quest_moderation_state_valid CHECK (
        state IN ('UNREVIEWED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'UNDER_REVIEW', 'HIDDEN')
    ),
    CONSTRAINT quest_moderation_version_valid CHECK (version >= 0),
    CONSTRAINT quest_moderation_reason_valid CHECK (
        reason_code IS NULL OR char_length(reason_code) BETWEEN 1 AND 40
    ),
    CONSTRAINT quest_moderation_review_time_valid CHECK (
        reviewed_at IS NULL OR submitted_at IS NOT NULL AND reviewed_at >= submitted_at
    )
);

CREATE INDEX quest_moderation_state_idx ON quest_moderation (state, submitted_at);
--rollback DROP TABLE quest_moderation;
