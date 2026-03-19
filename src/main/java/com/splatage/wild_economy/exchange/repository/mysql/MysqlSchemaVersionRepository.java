package com.splatage.wild_economy.exchange.repository.mysql;

import com.splatage.wild_economy.exchange.repository.SchemaVersionRepository;

public final class MysqlSchemaVersionRepository implements SchemaVersionRepository {

    @Override
    public int getCurrentVersion() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setCurrentVersion(final int version) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
