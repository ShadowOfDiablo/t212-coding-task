-- What the position traded: coins bought, average buy price (fees included) and average sell price.
-- NULL for positions stored before these columns existed; exit_price is NULL until the first sell.
ALTER TABLE position ADD COLUMN IF NOT EXISTS quantity NUMERIC;
ALTER TABLE position ADD COLUMN IF NOT EXISTS entry_price NUMERIC;
ALTER TABLE position ADD COLUMN IF NOT EXISTS exit_price NUMERIC;
