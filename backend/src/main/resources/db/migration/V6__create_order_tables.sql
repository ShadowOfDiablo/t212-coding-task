CREATE TYPE order_side AS ENUM ('BUY', 'SELL');

CREATE TYPE order_type AS ENUM ('MARKET');

CREATE TYPE order_amount_type AS ENUM ('BASE_QUANTITY', 'QUOTE_AMOUNT');

-- Binance's order statuses, by their wire names.
CREATE TYPE order_status AS ENUM ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELED', 'REJECTED',
                                  'EXPIRED', 'EXPIRED_IN_MATCH');

-- Live orders: open, executed and refused. A Binance order id is unique per symbol only. A refused
-- order has no exchange id and no execution numbers, only an error.
CREATE TABLE orders (
    id                BIGSERIAL         PRIMARY KEY,
    ticker_id         BIGINT            NOT NULL REFERENCES ticker (id),
    exchange_order_id BIGINT,
    side              order_side        NOT NULL,
    order_type        order_type        NOT NULL,
    amount_type       order_amount_type NOT NULL,
    status            order_status      NOT NULL,
    amount            NUMERIC           NOT NULL CHECK (amount > 0),
    executed_quantity NUMERIC,
    quote_amount      NUMERIC,
    average_price     NUMERIC,
    error_code        INTEGER,
    error_message     TEXT,
    -- the order's total fee; NULL when refused, open, or paid in more than one coin
    fee_amount        NUMERIC,
    fee_currency      TEXT,
    created_at        TIMESTAMPTZ       NOT NULL,
    updated_at        TIMESTAMPTZ       NOT NULL,
    UNIQUE (ticker_id, exchange_order_id),
    CHECK ((exchange_order_id IS NULL) = (error_code IS NOT NULL)),
    CHECK ((fee_amount IS NULL) = (fee_currency IS NULL))
);

CREATE INDEX orders_ticker_updated_at_idx ON orders (ticker_id, updated_at DESC);

