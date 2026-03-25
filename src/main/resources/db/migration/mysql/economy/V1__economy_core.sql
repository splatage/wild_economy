CREATE TABLE IF NOT EXISTS ${economy_prefix}economy_accounts (
    player_uuid VARCHAR(36) PRIMARY KEY,
    balance_minor BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    INDEX idx_${economy_prefix}economy_accounts_balance_minor (balance_minor)
);

CREATE TABLE IF NOT EXISTS ${economy_prefix}economy_ledger (
    entry_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    entry_type VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL,
    balance_after_minor BIGINT NOT NULL,
    counterparty_uuid VARCHAR(36) NULL,
    reference_type VARCHAR(64) NULL,
    reference_id VARCHAR(128) NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_${economy_prefix}economy_ledger_player_created_at (player_uuid, created_at),
    INDEX idx_${economy_prefix}economy_ledger_reference (reference_type, reference_id)
);

CREATE TABLE IF NOT EXISTS ${economy_prefix}economy_name_cache (
    player_uuid VARCHAR(36) PRIMARY KEY,
    last_name VARCHAR(64) NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_${economy_prefix}economy_name_cache_last_name (last_name)
);

CREATE TABLE IF NOT EXISTS ${economy_prefix}schema_version (
    version INT NOT NULL PRIMARY KEY,
    applied_at BIGINT NOT NULL
);
