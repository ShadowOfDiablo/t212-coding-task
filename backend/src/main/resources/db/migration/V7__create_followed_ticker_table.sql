CREATE TABLE IF NOT EXISTS followed_ticker (
    broker      text        NOT NULL,
    symbol      text        NOT NULL,
    followed_at timestamptz NOT NULL,
    PRIMARY KEY (broker, symbol)
);
