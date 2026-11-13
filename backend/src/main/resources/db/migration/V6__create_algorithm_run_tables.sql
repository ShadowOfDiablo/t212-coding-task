-- Runs of trading algorithms, in training and in live trading. A run row is written when the run
-- starts and updated once when it ends; the settings it used are recorded at start, at every saved
-- change and at the end.
CREATE TABLE IF NOT EXISTS algorithm_run (
    id                  BIGSERIAL   PRIMARY KEY,
    mode                TEXT        NOT NULL,
    broker              TEXT        NOT NULL,
    ticker              TEXT        NOT NULL,
    status              TEXT        NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    -- the replayed range, training only
    from_time           TIMESTAMPTZ,
    to_time             TIMESTAMPTZ,
    starting_value      NUMERIC     NOT NULL,
    final_value         NUMERIC,
    buy_count           INT         NOT NULL,
    sell_count          INT         NOT NULL,
    stop_loss_count     INT         NOT NULL,
    refused_order_count INT         NOT NULL,
    failure_reason      TEXT
);

CREATE INDEX IF NOT EXISTS algorithm_run_broker_idx ON algorithm_run (broker, started_at);

CREATE TABLE IF NOT EXISTS algorithm_run_settings (
    run_id      BIGINT      NOT NULL REFERENCES algorithm_run (id),
    recorded_at TIMESTAMPTZ NOT NULL,
    event       TEXT        NOT NULL,
    settings_id BIGINT      NOT NULL REFERENCES algorithm_settings (id),
    PRIMARY KEY (run_id, recorded_at, event)
);

-- The portfolio value of a training run over time, thinned to at most 1,000 points.
CREATE TABLE IF NOT EXISTS training_portfolio_point (
    run_id     BIGINT      NOT NULL REFERENCES algorithm_run (id),
    point_time TIMESTAMPTZ NOT NULL,
    cash       NUMERIC     NOT NULL,
    value      NUMERIC     NOT NULL,
    PRIMARY KEY (run_id, point_time)
);
