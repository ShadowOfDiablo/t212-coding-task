-- The bot's positions, kept as FIFO lots. A position is found by the run it opened in and the time
-- of its first buy, so a position written again is updated, not duplicated.
CREATE TABLE IF NOT EXISTS position (
    id           BIGSERIAL   PRIMARY KEY,
    run_id       BIGINT      NOT NULL REFERENCES algorithm_run (id),
    broker       TEXT        NOT NULL,
    ticker       TEXT        NOT NULL,
    status       TEXT        NOT NULL,
    opened_at    TIMESTAMPTZ NOT NULL,
    closed_at    TIMESTAMPTZ,
    cost         NUMERIC     NOT NULL,
    realised_pnl NUMERIC     NOT NULL,
    mae          NUMERIC     NOT NULL,
    mfe          NUMERIC     NOT NULL,
    UNIQUE (run_id, opened_at)
);
CREATE INDEX IF NOT EXISTS position_open_idx ON position (broker, ticker, status);

-- Buys that still hold coins. A lot that is sold completely is deleted.
CREATE TABLE IF NOT EXISTS open_lot (
    id          BIGSERIAL   PRIMARY KEY,
    position_id BIGINT      NOT NULL REFERENCES position (id),
    run_id      BIGINT      NOT NULL REFERENCES algorithm_run (id),
    bought_at   TIMESTAMPTZ NOT NULL,
    quantity    NUMERIC     NOT NULL,
    entry_price NUMERIC     NOT NULL
);
CREATE INDEX IF NOT EXISTS open_lot_position_idx ON open_lot (position_id);

-- One row per lot, or part of a lot, that a sell used. Rows are only ever added.
CREATE TABLE IF NOT EXISTS trade (
    id           BIGSERIAL   PRIMARY KEY,
    position_id  BIGINT      NOT NULL REFERENCES position (id),
    run_id       BIGINT      NOT NULL REFERENCES algorithm_run (id),
    bought_at    TIMESTAMPTZ NOT NULL,
    sold_at      TIMESTAMPTZ NOT NULL,
    quantity     NUMERIC     NOT NULL,
    entry_price  NUMERIC     NOT NULL,
    exit_price   NUMERIC     NOT NULL,
    fee          NUMERIC     NOT NULL,
    realised_pnl NUMERIC     NOT NULL
);
CREATE INDEX IF NOT EXISTS trade_run_idx ON trade (run_id, sold_at);
