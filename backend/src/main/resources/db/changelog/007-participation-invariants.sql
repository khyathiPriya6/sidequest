--liquibase formatted sql
--changeset sidequest:007-participation-invariants splitStatements:false
-- Preserve previously applied changesets; give trigger violations stable diagnostics.
CREATE OR REPLACE FUNCTION prevent_participation_snapshot_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.id IS DISTINCT FROM NEW.id
       OR OLD.user_id IS DISTINCT FROM NEW.user_id
       OR OLD.quest_id IS DISTINCT FROM NEW.quest_id
       OR OLD.reward_xp IS DISTINCT FROM NEW.reward_xp
       OR OLD.joined_at IS DISTINCT FROM NEW.joined_at THEN
        RAISE EXCEPTION 'participation snapshot is immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'participations_immutable_snapshot';
    END IF;
    IF OLD.status = 'COMPLETED' AND
       (NEW.status IS DISTINCT FROM OLD.status OR NEW.completed_at IS DISTINCT FROM OLD.completed_at) THEN
        RAISE EXCEPTION 'participation completion is irreversible'
            USING ERRCODE = '23514', CONSTRAINT = 'participations_completion_immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION prevent_xp_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'xp_ledger is append-only'
        USING ERRCODE = '23514', CONSTRAINT = 'xp_ledger_append_only';
END;
$$ LANGUAGE plpgsql;
--rollback CREATE OR REPLACE FUNCTION prevent_participation_snapshot_mutation() RETURNS TRIGGER AS $$ BEGIN IF OLD.id IS DISTINCT FROM NEW.id OR OLD.user_id IS DISTINCT FROM NEW.user_id OR OLD.quest_id IS DISTINCT FROM NEW.quest_id OR OLD.reward_xp IS DISTINCT FROM NEW.reward_xp OR OLD.joined_at IS DISTINCT FROM NEW.joined_at THEN RAISE EXCEPTION 'participation ownership, reward snapshot, id and joinedAt are immutable'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql;
--rollback CREATE OR REPLACE FUNCTION prevent_xp_ledger_mutation() RETURNS TRIGGER AS $$ BEGIN RAISE EXCEPTION 'xp_ledger is append-only'; END; $$ LANGUAGE plpgsql;
