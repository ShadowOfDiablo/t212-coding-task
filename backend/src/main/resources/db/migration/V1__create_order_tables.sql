-- Orders that are not finished yet. A row is written before the order is sent and removed
-- once the order reaches a final state, so this table only ever holds active orders.
CREATE TABLE open_order (
    client_order_id   TEXT        PRIMARY KEY,
    exchange_order_id BIGINT      UNIQUE,
    broker            TEXT        NOT NULL,
    ticker            TEXT        NOT NULL,
    side              TEXT        NOT NULL,
    order_type        TEXT        NOT NULL,
    amount            NUMERIC     NOT NULL,
    amount_type       TEXT        NOT NULL,
    status            TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX open_order_broker_idx ON open_order (broker);

-- Finished orders. Rows are only ever added.
CREATE TABLE order_history (
    client_order_id   TEXT        PRIMARY KEY,
    exchange_order_id BIGINT      UNIQUE,
    broker            TEXT        NOT NULL,
    ticker            TEXT        NOT NULL,
    side              TEXT        NOT NULL,
    order_type        TEXT        NOT NULL,
    amount            NUMERIC     NOT NULL,
    amount_type       TEXT        NOT NULL,
    final_status      TEXT        NOT NULL,
    executed_quantity NUMERIC     NOT NULL,
    quote_amount      NUMERIC     NOT NULL,
    average_price     NUMERIC,
    error_code        INTEGER,
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL,
    closed_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX order_history_broker_closed_at_idx ON order_history (broker, closed_at DESC);

-- Each piece an order was filled in, with its own price and fee.
CREATE TABLE order_fill (
    client_order_id  TEXT    NOT NULL,
    trade_id         BIGINT  NOT NULL,
    price            NUMERIC NOT NULL,
    quantity         NUMERIC NOT NULL,
    commission       NUMERIC NOT NULL,
    commission_asset TEXT    NOT NULL,
    PRIMARY KEY (client_order_id, trade_id)
);
