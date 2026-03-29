CREATE TABLE IF NOT EXISTS ${store_prefix}store_entitlements (
    player_uuid TEXT NOT NULL,
    entitlement_key TEXT NOT NULL,
    product_id TEXT NOT NULL,
    granted_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, entitlement_key)
);

CREATE TABLE IF NOT EXISTS ${store_prefix}store_purchases (
    purchase_id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    product_id TEXT NOT NULL,
    product_type TEXT NOT NULL,
    price_minor INTEGER NOT NULL,
    status TEXT NOT NULL,
    failure_reason TEXT NULL,
    created_at INTEGER NOT NULL,
    completed_at INTEGER NULL
);

CREATE INDEX IF NOT EXISTS idx_${store_prefix}store_entitlements_product_id
    ON ${store_prefix}store_entitlements (product_id);

CREATE INDEX IF NOT EXISTS idx_${store_prefix}store_purchases_player_created_at
    ON ${store_prefix}store_purchases (player_uuid, created_at);

CREATE INDEX IF NOT EXISTS idx_${store_prefix}store_purchases_product_status
    ON ${store_prefix}store_purchases (product_id, status);

CREATE TABLE IF NOT EXISTS ${store_prefix}store_schema_version (
    version INTEGER NOT NULL PRIMARY KEY,
    applied_at INTEGER NOT NULL
);

