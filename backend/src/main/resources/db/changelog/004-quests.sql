--liquibase formatted sql
--changeset sidequest:004-quests
CREATE TABLE quests (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(3000),
    category VARCHAR(16),
    difficulty VARCHAR(16),
    reward_xp INTEGER,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT quests_creator_fk
        FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT quests_title_valid CHECK (
        title = regexp_replace(title, '^[[:space:]]+|[[:space:]]+$', '', 'g')
        AND char_length(title) BETWEEN 3 AND 120
    ),
    CONSTRAINT quests_description_valid CHECK (
        description IS NULL OR (
            description = regexp_replace(description, '^[[:space:]]+|[[:space:]]+$', '', 'g')
            AND char_length(description) BETWEEN 10 AND 3000
        )
    ),
    CONSTRAINT quests_category_valid CHECK (
        category IS NULL OR category IN ('ADVENTURE', 'CREATIVITY', 'FITNESS', 'LEARNING', 'FOOD', 'SOCIAL')
    ),
    CONSTRAINT quests_difficulty_valid CHECK (
        difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')
    ),
    CONSTRAINT quests_reward_xp_valid CHECK (reward_xp IS NULL OR reward_xp BETWEEN 1 AND 1000),
    CONSTRAINT quests_status_valid CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT quests_version_nonnegative CHECK (version >= 0),
    CONSTRAINT quests_timestamps_valid CHECK (
        updated_at >= created_at
        AND (published_at IS NULL OR published_at >= created_at)
        AND (archived_at IS NULL OR archived_at >= created_at)
        AND (published_at IS NULL OR archived_at IS NULL OR archived_at >= published_at)
    ),
    CONSTRAINT quests_lifecycle_valid CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND archived_at IS NULL)
        OR (
            status = 'PUBLISHED'
            AND published_at IS NOT NULL
            AND archived_at IS NULL
            AND description IS NOT NULL
            AND category IS NOT NULL
            AND difficulty IS NOT NULL
            AND reward_xp IS NOT NULL
        )
        OR (status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX quests_creator_created_idx ON quests (creator_id, created_at DESC, id DESC);
CREATE INDEX quests_public_created_idx ON quests (created_at DESC, id DESC) WHERE status = 'PUBLISHED';
--rollback DROP TABLE quests;
