CREATE TABLE IF NOT EXISTS ${store_prefix}store_entitlements (
    player_uuid VARCHAR(36) NOT NULL,
    entitlement_key VARCHAR(128) NOT NULL,
    product_id VARCHAR(128) NOT NULL,
    granted_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, entitlement_key),
    INDEX idx_${store_prefix}store_entitlements_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS ${store_prefix}store_purchases (
    purchase_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    product_id VARCHAR(128) NOT NULL,
    product_type VARCHAR(64) NOT NULL,
    price_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    created_at BIGINT NOT NULL,
    completed_at BIGINT NULL,
    INDEX idx_${store_prefix}store_purchases_player_created_at (player_uuid, created_at),
    INDEX idx_${store_prefix}store_purchases_product_status (product_id, status)
);

CREATE TABLE IF NOT EXISTS ${store_prefix}store_schema_version (
    version INT NOT NULL PRIMARY KEY,
    applied_at BIGINT NOT NULL
);

