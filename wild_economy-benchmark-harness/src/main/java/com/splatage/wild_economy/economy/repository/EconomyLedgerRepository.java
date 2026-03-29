package com.splatage.wild_economy.economy.repository;

import com.splatage.wild_economy.economy.model.EconomyLedgerEntry;
import java.sql.Connection;

public interface EconomyLedgerRepository {
    void insert(Connection connection, EconomyLedgerEntry entry);
}
