package com.splatage.wild_economy.gui;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MenuSessionStore {

    private final ConcurrentMap<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    public void put(final MenuSession session) {
        Objects.requireNonNull(session, "session");
        this.sessions.put(session.playerId(), session);
    }

    public MenuSession get(final UUID playerId) {
        return this.sessions.get(playerId);
    }

    public void remove(final UUID playerId) {
        this.sessions.remove(playerId);
    }
}
