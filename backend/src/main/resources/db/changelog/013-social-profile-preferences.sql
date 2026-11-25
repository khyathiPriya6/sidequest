--liquibase formatted sql
--changeset sidequest:013-social-profile-preferences
-- Privacy is opt-in. Rows are created lazily when an owner reads their settings,
-- not during public profile lookup. No existing account becomes public.
CREATE TABLE social_profile_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    public_id UUID NOT NULL UNIQUE,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT social_visibility_check CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC')),
    CONSTRAINT social_version_nonnegative CHECK (version >= 0)
);
CREATE INDEX social_profile_visibility_idx ON social_profile_preferences (visibility, public_id);
--rollback DROP TABLE social_profile_preferences;
