CREATE TABLE IF NOT EXISTS ${exchange_prefix}supplier_all_time (
    player_uuid TEXT NOT NULL,
    item_key TEXT NOT NULL,
    quantity_sold INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, item_key)
);

CREATE INDEX IF NOT EXISTS idx_${exchange_prefix}supplier_all_time_player
    ON ${exchange_prefix}supplier_all_time (player_uuid);

CREATE TABLE IF NOT EXISTS ${exchange_prefix}supplier_weekly (
    week_key TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    item_key TEXT NOT NULL,
    quantity_sold INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (week_key, player_uuid, item_key)
);

CREATE INDEX IF NOT EXISTS idx_${exchange_prefix}supplier_weekly_scope
    ON ${exchange_prefix}supplier_weekly (week_key, player_uuid);
