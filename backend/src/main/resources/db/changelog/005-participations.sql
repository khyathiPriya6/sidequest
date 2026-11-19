--liquibase formatted sql
--changeset sidequest:005-participations splitStatements:false
CREATE TABLE participations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    quest_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    reward_xp INTEGER NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT participations_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT participations_quest_fk FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE RESTRICT,
    CONSTRAINT participations_unique_user_quest UNIQUE (user_id, quest_id),
    CONSTRAINT participations_status_valid CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT participations_reward_valid CHECK (reward_xp BETWEEN 1 AND 1000),
    CONSTRAINT participations_completed_state_valid CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= joined_at)
    )
);

CREATE INDEX participations_owner_joined_idx
    ON participations (user_id, joined_at DESC, id DESC);

CREATE OR REPLACE FUNCTION prevent_participation_snapshot_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.id IS DISTINCT FROM NEW.id
       OR OLD.user_id IS DISTINCT FROM NEW.user_id
       OR OLD.quest_id IS DISTINCT FROM NEW.quest_id
       OR OLD.reward_xp IS DISTINCT FROM NEW.reward_xp
       OR OLD.joined_at IS DISTINCT FROM NEW.joined_at THEN
        RAISE EXCEPTION 'participation ownership, reward snapshot, id and joinedAt are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER participations_immutable_snapshot
BEFORE UPDATE ON participations
FOR EACH ROW EXECUTE FUNCTION prevent_participation_snapshot_mutation();
--rollback DROP TRIGGER participations_immutable_snapshot ON participations;
--rollback DROP FUNCTION prevent_participation_snapshot_mutation();
--rollback DROP TABLE participations;
