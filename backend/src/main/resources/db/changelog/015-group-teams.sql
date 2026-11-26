--liquibase formatted sql
--changeset sidequest:015-group-teams
CREATE TABLE teams (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 name VARCHAR(80) NOT NULL,
 state VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT team_state CHECK (state IN ('ACTIVE','ARCHIVED')),
 CONSTRAINT team_name_len CHECK (char_length(trim(name)) BETWEEN 3 AND 80),
 CONSTRAINT team_version CHECK (version >= 0)
);
CREATE INDEX teams_owner_idx ON teams(owner_id,state);
CREATE TABLE team_members (
 team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 role VARCHAR(8) NOT NULL,
 state VARCHAR(12) NOT NULL,
 joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(team_id,user_id),
 CONSTRAINT team_member_role CHECK(role IN ('OWNER','MEMBER')),
 CONSTRAINT team_member_state CHECK(state IN ('ACTIVE','LEFT','REMOVED')),
 CONSTRAINT team_member_version CHECK(version>=0)
);
CREATE INDEX team_member_user_idx ON team_members(user_id,state,team_id);
CREATE TABLE team_invitations (
 id UUID PRIMARY KEY,
 team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
 invited_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 sender_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(team_id, invited_user_id),
 CONSTRAINT team_invite_state CHECK(status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
 CONSTRAINT team_invite_version CHECK(version>=0)
);
CREATE INDEX team_invitations_recipient_idx ON team_invitations(invited_user_id,status,created_at);
CREATE INDEX team_invitations_sender_idx ON team_invitations(sender_id,created_at);
CREATE TABLE team_challenges (
 id UUID PRIMARY KEY,
 team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
 quest_id UUID NOT NULL REFERENCES quests(id) ON DELETE RESTRICT,
 target_completions INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ends_at TIMESTAMPTZ NOT NULL,
 UNIQUE(team_id,quest_id),
 CONSTRAINT team_target CHECK(target_completions BETWEEN 1 AND 20),
 CONSTRAINT team_challenge_dates CHECK(ends_at>created_at)
);
CREATE INDEX team_challenges_by_team_idx ON team_challenges(team_id,created_at DESC,id);
--rollback DROP TABLE team_challenges;
--rollback DROP TABLE team_invitations;
--rollback DROP TABLE team_members;
--rollback DROP TABLE teams;
