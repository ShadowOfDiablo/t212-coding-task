DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'position_status') THEN
        CREATE TYPE position_status AS ENUM ('OPEN', 'CLOSED');
    END IF;
END $$;

-- Positions at average cost. Found again by the run they opened in and the time of the first buy,
-- so a position written twice is updated, not duplicated.
CREATE TABLE IF NOT EXISTS position (
    id           BIGSERIAL       PRIMARY KEY,
    run_id       BIGINT          NOT NULL REFERENCES algorithm_run (id),
    status       position_status NOT NULL,
    opened_at    TIMESTAMPTZ     NOT NULL,
    closed_at    TIMESTAMPTZ,
    cost         NUMERIC         NOT NULL,
    realised_pnl NUMERIC         NOT NULL,
    mae          NUMERIC         NOT NULL,
    mfe          NUMERIC         NOT NULL,
    UNIQUE (run_id, opened_at)
);
