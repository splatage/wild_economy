package com.splatage.wild_economy.exchange.repository;

public interface SchemaVersionRepository {
    int getCurrentVersion();
    void setCurrentVersion(int version);
}
