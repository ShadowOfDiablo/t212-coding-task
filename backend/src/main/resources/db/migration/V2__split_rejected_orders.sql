-- Orders the exchange refused. Nothing was executed, so there is no exchange order id,
-- no execution numbers and no fills; only the error. Rows are only ever added.
CREATE TABLE rejected_order (
    client_order_id TEXT        PRIMARY KEY,
    broker          TEXT        NOT NULL,
    ticker          TEXT        NOT NULL,
    side            TEXT        NOT NULL,
    order_type      TEXT        NOT NULL,
    amount          NUMERIC     NOT NULL,
    amount_type     TEXT        NOT NULL,
    error_code      INTEGER     NOT NULL,
    error_message   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    rejected_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX rejected_order_broker_rejected_at_idx ON rejected_order (broker, rejected_at DESC);

INSERT INTO rejected_order (client_order_id, broker, ticker, side, order_type, amount,
                            amount_type, error_code, error_message, created_at, rejected_at)
SELECT client_order_id, broker, ticker, side, order_type, amount, amount_type,
       error_code, error_message, created_at, closed_at
FROM order_history
WHERE final_status = 'REJECTED';

DELETE FROM order_history WHERE final_status = 'REJECTED';

-- order_history now only holds executed orders, so every execution column is always set.
ALTER TABLE order_history DROP COLUMN error_code;
ALTER TABLE order_history DROP COLUMN error_message;
ALTER TABLE order_history ALTER COLUMN exchange_order_id SET NOT NULL;
ALTER TABLE order_history ALTER COLUMN average_price SET NOT NULL;
