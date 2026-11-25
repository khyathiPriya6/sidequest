--liquibase formatted sql
--changeset sidequest:012-gamification-indexes
CREATE INDEX xp_ledger_period_idx ON xp_ledger (awarded_at, participation_id) INCLUDE (amount);
CREATE INDEX participations_completed_owner_idx ON participations (user_id, completed_at, id) WHERE status = 'COMPLETED';
--rollback DROP INDEX participations_completed_owner_idx;
--rollback DROP INDEX xp_ledger_period_idx;
