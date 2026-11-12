-- Live orders are keyed by Binance's own order id. Our tb- client order id stays only as a
-- marker that the bot placed the order.

-- Executed orders.
ALTER TABLE order_history DROP CONSTRAINT order_history_pkey;
ALTER TABLE order_history DROP CONSTRAINT order_history_exchange_order_id_key;
ALTER TABLE order_history ADD PRIMARY KEY (exchange_order_id);
ALTER TABLE order_history ALTER COLUMN client_order_id DROP NOT NULL;

-- Fills point at the executed order through Binance's order id.
ALTER TABLE order_fill ADD COLUMN exchange_order_id BIGINT;
UPDATE order_fill f
SET exchange_order_id = h.exchange_order_id
FROM order_history h
WHERE h.client_order_id = f.client_order_id;
DELETE FROM order_fill WHERE exchange_order_id IS NULL;
ALTER TABLE order_fill ALTER COLUMN exchange_order_id SET NOT NULL;
ALTER TABLE order_fill DROP CONSTRAINT order_fill_pkey;
ALTER TABLE order_fill DROP COLUMN client_order_id;
ALTER TABLE order_fill ADD PRIMARY KEY (exchange_order_id, trade_id);

-- Active orders now only hold orders Binance acknowledged that still rest on the book.
-- Rows written before sending (SENDING / UNKNOWN) have no Binance order id and can't be kept.
DROP TABLE IF EXISTS open_order;
CREATE TABLE IF NOT EXISTS open_order (
    exchange_order_id BIGINT      PRIMARY KEY,
    client_order_id   TEXT,
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

CREATE INDEX IF NOT EXISTS open_order_broker_idx ON open_order (broker);

-- Binance gives a refused order no id, so rejected orders get their own.
ALTER TABLE rejected_order DROP CONSTRAINT rejected_order_pkey;
ALTER TABLE rejected_order DROP COLUMN client_order_id;
ALTER TABLE rejected_order DROP COLUMN created_at;
ALTER TABLE rejected_order ADD COLUMN id BIGSERIAL PRIMARY KEY;
