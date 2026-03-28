CREATE INDEX IF NOT EXISTS idx_${exchange_prefix}exchange_transactions_type_created
    ON ${exchange_prefix}exchange_transactions (transaction_type, created_at);

CREATE INDEX IF NOT EXISTS idx_${exchange_prefix}exchange_transactions_type_player_created
    ON ${exchange_prefix}exchange_transactions (transaction_type, player_uuid, created_at);

CREATE INDEX IF NOT EXISTS idx_${exchange_prefix}supplier_all_time_updated
    ON ${exchange_prefix}supplier_all_time (updated_at);
