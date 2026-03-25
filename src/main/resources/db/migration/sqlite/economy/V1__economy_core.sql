CREATE TABLE IF NOT EXISTS ${economy_prefix}economy_accounts (
    player_uuid TEXT PRIMARY KEY,
    balance_minor INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS ${economy_prefix}economy_ledger (
    entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    entry_type TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    balance_after_minor INTEGER NOT NULL,
    counterparty_uuid TEXT NULL,
    reference_type TEXT NULL,
    reference_id TEXT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS ${economy_prefix}economy_name_cache (
    player_uuid TEXT PRIMARY KEY,
    last_name TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS ${economy_prefix}schema_version (
    version INTEGER NOT NULL PRIMARY KEY,
    applied_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_${economy_prefix}economy_accounts_balance_minor
    ON ${economy_prefix}economy_accounts (balance_minor);

CREATE INDEX IF NOT EXISTS idx_${economy_prefix}economy_ledger_player_created_at
    ON ${economy_prefix}economy_ledger (player_uuid, created_at);

CREATE INDEX IF NOT EXISTS idx_${economy_prefix}economy_ledger_reference
    ON ${economy_prefix}economy_ledger (reference_type, reference_id);

CREATE INDEX IF NOT EXISTS idx_${economy_prefix}economy_name_cache_last_name
    ON ${economy_prefix}economy_name_cache (last_name);
