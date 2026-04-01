package com.splatage.wild_economy.title.service;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class PersistentDataTitleSelectionService implements TitleSelectionService {

    private final NamespacedKey selectionKey;

    public PersistentDataTitleSelectionService(final String namespace) {
        final String normalizedNamespace = Objects.requireNonNull(namespace, "namespace").trim().toLowerCase(Locale.ROOT);
        this.selectionKey = Objects.requireNonNull(NamespacedKey.fromString(normalizedNamespace + ":title_selection"));
    }

    @Override
    public Optional<String> getSelectedTitleKey(final Player player) {
        final String stored = player.getPersistentDataContainer().get(this.selectionKey, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(stored);
    }

    @Override
    public Optional<String> getSelectedTitleKey(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Optional.empty();
        }
        return this.getSelectedTitleKey(player);
    }

    @Override
    public void setSelectedTitleKey(final Player player, final String titleKey) {
        Objects.requireNonNull(player, "player");
        final String trimmed = Objects.requireNonNull(titleKey, "titleKey").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("titleKey cannot be blank");
        }
        player.getPersistentDataContainer().set(this.selectionKey, PersistentDataType.STRING, trimmed);
    }

    @Override
    public void clearSelectedTitleKey(final Player player) {
        Objects.requireNonNull(player, "player");
        player.getPersistentDataContainer().remove(this.selectionKey);
    }
}
