--liquibase formatted sql
--changeset sidequest:011-legacy-moderation-intake
-- Legacy PUBLISHED Quests remain unapproved until individual staff intake and review.
-- Provenance is immutable; absent provenance means legacy approval is forbidden.
CREATE TABLE legacy_moderation_intake (
    quest_id UUID PRIMARY KEY REFERENCES quests(id) ON DELETE RESTRICT,
    moderator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE legacy_moderation_intake;
