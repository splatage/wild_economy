CREATE TABLE IF NOT EXISTS ${exchange_prefix}exchange_stock (
    item_key VARCHAR(128) PRIMARY KEY,
    stock_count BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS ${exchange_prefix}exchange_transactions (
    transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_type VARCHAR(32) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    item_key VARCHAR(128) NOT NULL,
    amount INT NOT NULL,
    unit_price DECIMAL(18,4) NOT NULL,
    total_value DECIMAL(18,4) NOT NULL,
    created_at BIGINT NOT NULL,
    meta_json TEXT NULL
);

CREATE TABLE IF NOT EXISTS ${exchange_prefix}schema_version (
    version INT NOT NULL PRIMARY KEY,
    applied_at BIGINT NOT NULL
);
