CREATE TABLE IF NOT EXISTS ${exchange_prefix}exchange_stock (
    item_key TEXT PRIMARY KEY,
    stock_count INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS ${exchange_prefix}exchange_transactions (
    transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_type TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    item_key TEXT NOT NULL,
    amount INTEGER NOT NULL,
    unit_price NUMERIC NOT NULL,
    total_value NUMERIC NOT NULL,
    created_at INTEGER NOT NULL,
    meta_json TEXT NULL
);

CREATE TABLE IF NOT EXISTS ${exchange_prefix}exchange_schema_version (
    version INTEGER NOT NULL PRIMARY KEY,
    applied_at INTEGER NOT NULL
);
