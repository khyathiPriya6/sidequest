--liquibase formatted sql
--changeset sidequest:006-xp-ledger splitStatements:false
CREATE TABLE xp_ledger (
    id UUID PRIMARY KEY,
    participation_id UUID NOT NULL,
    amount INTEGER NOT NULL,
    awarded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT xp_ledger_participation_fk FOREIGN KEY (participation_id)
        REFERENCES participations(id) ON DELETE RESTRICT,
    CONSTRAINT xp_ledger_unique_participation UNIQUE (participation_id),
    CONSTRAINT xp_ledger_amount_valid CHECK (amount BETWEEN 1 AND 1000)
);

CREATE OR REPLACE FUNCTION prevent_xp_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'xp_ledger is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER xp_ledger_append_only
BEFORE UPDATE OR DELETE ON xp_ledger
FOR EACH ROW EXECUTE FUNCTION prevent_xp_ledger_mutation();
--rollback DROP TRIGGER xp_ledger_append_only ON xp_ledger;
--rollback DROP FUNCTION prevent_xp_ledger_mutation();
--rollback DROP TABLE xp_ledger;
