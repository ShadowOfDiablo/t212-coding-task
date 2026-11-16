DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bot_mode') THEN
        CREATE TYPE bot_mode AS ENUM ('TRADING', 'TRAINING');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'run_status') THEN
        CREATE TYPE run_status AS ENUM ('STARTED', 'COMPLETED', 'STOPPED', 'FAILED');
    END IF;
END $$;

-- Written when the run starts and updated once when it ends.
CREATE TABLE IF NOT EXISTS algorithm_run (
    id                  BIGSERIAL   PRIMARY KEY,
    ticker_id           BIGINT      NOT NULL REFERENCES ticker (id),
    algorithm_id        BIGINT      NOT NULL REFERENCES algorithm (id),
    mode                bot_mode    NOT NULL,
    status              run_status  NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    -- the replayed range, training only
    from_time           TIMESTAMPTZ,
    to_time             TIMESTAMPTZ,
    starting_value      NUMERIC     NOT NULL,
    final_value         NUMERIC,
    buy_count           INTEGER     NOT NULL,
    sell_count          INTEGER     NOT NULL,
    stop_loss_count     INTEGER     NOT NULL,
    refused_order_count INTEGER     NOT NULL,
    failure_reason      TEXT,
    CHECK ((from_time IS NULL) = (to_time IS NULL)),
    CHECK (from_time < to_time)
);

CREATE INDEX IF NOT EXISTS algorithm_run_ticker_idx ON algorithm_run (ticker_id, started_at);

-- Thinned to at most 1,000 points per run.
CREATE TABLE IF NOT EXISTS training_portfolio_point (
    run_id     BIGINT      NOT NULL REFERENCES algorithm_run (id),
    point_time TIMESTAMPTZ NOT NULL,
    cash       NUMERIC     NOT NULL,
    value      NUMERIC     NOT NULL,
    PRIMARY KEY (run_id, point_time)
);

-- The variant the live bot last traded each ticker with.
CREATE TABLE IF NOT EXISTS active_algorithm (
    ticker_id    BIGINT      PRIMARY KEY REFERENCES ticker (id),
    algorithm_id BIGINT      NOT NULL REFERENCES algorithm (id),
    activated_at TIMESTAMPTZ NOT NULL
);
