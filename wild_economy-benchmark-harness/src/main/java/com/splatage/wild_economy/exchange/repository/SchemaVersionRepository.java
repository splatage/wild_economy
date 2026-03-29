package com.splatage.wild_economy.exchange.repository;

public interface SchemaVersionRepository {
    int getCurrentVersion(String tableName);
    void setCurrentVersion(String tableName, int version);
}
