--liquibase formatted sql
--changeset sidequest:009-quest-safety-operations
-- Roles are provisioned out-of-band. Never grant a moderator role through signup.
CREATE TABLE moderation_staff (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE quest_reports (
    id UUID PRIMARY KEY,
    quest_id UUID NOT NULL REFERENCES quests(id) ON DELETE RESTRICT,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT quest_reports_reason_check CHECK (reason IN
        ('DANGEROUS_ACT', 'SELF_HARM', 'ILLEGAL', 'VIOLENCE_HARASSMENT', 'HATE',
         'EXPLOITATION', 'FRAUD_SPAM', 'PRIVACY', 'OTHER')),
    CONSTRAINT quest_reports_one_per_account UNIQUE (quest_id, reporter_id)
);
CREATE INDEX quest_reports_quest_idx ON quest_reports (quest_id, created_at DESC);
CREATE TABLE moderation_audit (
    id UUID PRIMARY KEY,
    quest_id UUID NOT NULL REFERENCES quests(id) ON DELETE RESTRICT,
    moderator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    previous_state VARCHAR(24) NOT NULL,
    next_state VARCHAR(24) NOT NULL,
    reason_code VARCHAR(40),
    decided_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX moderation_audit_quest_idx ON moderation_audit (quest_id, decided_at DESC);
--rollback DROP TABLE moderation_audit;
--rollback DROP TABLE quest_reports;
--rollback DROP TABLE moderation_staff;
