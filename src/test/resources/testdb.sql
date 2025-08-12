CREATE SCHEMA IF NOT EXISTS stock_prices_schema;
CREATE TABLE IF NOT EXISTS stock_prices_schema.stock_prices_1day (
    id SERIAL PRIMARY KEY,
    open DOUBLE PRECISION,
    close DOUBLE PRECISION,
    low DOUBLE PRECISION,
    high DOUBLE PRECISION,
    volume BIGINT,
    timestamp BIGINT,
    symbol VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);