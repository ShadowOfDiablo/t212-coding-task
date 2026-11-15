-- Take-profit sells, also counted in sell_count, like stop-loss sells.
ALTER TABLE algorithm_run
    ADD COLUMN take_profit_count INTEGER NOT NULL DEFAULT 0;
