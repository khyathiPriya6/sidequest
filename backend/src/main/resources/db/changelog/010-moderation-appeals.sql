--liquibase formatted sql
--changeset sidequest:010-moderation-appeals
-- Existing SAFETY-04 migration 009 is unchanged: avoid checksum drift.
ALTER TABLE quest_reports ADD COLUMN resolution VARCHAR(20) NOT NULL DEFAULT 'OPEN';
ALTER TABLE quest_reports ADD COLUMN resolved_at TIMESTAMPTZ;
ALTER TABLE quest_reports ADD COLUMN resolved_by UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE quest_reports ADD CONSTRAINT quest_reports_resolution_check CHECK (
    resolution IN ('OPEN','DISMISSED','ACTIONED')
);
ALTER TABLE quest_reports ADD CONSTRAINT quest_reports_resolution_fields CHECK (
    (resolution = 'OPEN' AND resolved_at IS NULL AND resolved_by IS NULL)
    OR (resolution <> 'OPEN' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
);
CREATE INDEX quest_reports_open_quest_idx ON quest_reports (quest_id) WHERE resolution = 'OPEN';

CREATE TABLE moderation_appeals (
    id UUID PRIMARY KEY,
    quest_id UUID NOT NULL REFERENCES quests(id) ON DELETE RESTRICT,
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    moderation_version BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT moderation_appeals_version CHECK (moderation_version >= 0),
    CONSTRAINT moderation_appeals_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT moderation_appeals_resolution CHECK (
        (status='PENDING' AND resolved_at IS NULL AND resolved_by IS NULL)
        OR (status <> 'PENDING' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
    ),
    CONSTRAINT moderation_appeals_unique_decision UNIQUE (quest_id, moderation_version)
);
CREATE INDEX moderation_appeals_pending_quest_idx ON moderation_appeals (quest_id) WHERE status = 'PENDING';
--rollback DROP TABLE moderation_appeals;
--rollback DROP INDEX quest_reports_open_quest_idx;
--rollback ALTER TABLE quest_reports DROP COLUMN resolved_by, DROP COLUMN resolved_at, DROP COLUMN resolution;
