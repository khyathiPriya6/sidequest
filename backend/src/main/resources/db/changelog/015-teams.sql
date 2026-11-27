--liquibase formatted sql
--changeset sidequest:015-teams
CREATE TABLE teams (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT teams_name_length CHECK (length(trim(name)) BETWEEN 3 AND 80)
);
CREATE TABLE team_members (
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id,user_id)
);
CREATE INDEX team_members_user_idx ON team_members(user_id,team_id);
CREATE TABLE team_invites (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    invitee_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    state VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(team_id,invitee_id),
    CONSTRAINT team_invite_state_ck CHECK(state IN ('PENDING','ACCEPTED','DECLINED','CANCELLED')),
    CONSTRAINT team_invite_version_ck CHECK(version>=0)
);
CREATE INDEX team_invites_incoming_idx ON team_invites(invitee_id,state,created_at DESC);
CREATE TABLE team_challenges (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    quest_id UUID NOT NULL REFERENCES quests(id) ON DELETE RESTRICT,
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(team_id,quest_id),
    CONSTRAINT team_challenge_window_ck CHECK(ends_at>starts_at)
);
CREATE INDEX team_challenge_team_idx ON team_challenges(team_id,starts_at DESC,id);
--rollback DROP TABLE team_challenges;
--rollback DROP TABLE team_invites;
--rollback DROP TABLE team_members;
--rollback DROP TABLE teams;
