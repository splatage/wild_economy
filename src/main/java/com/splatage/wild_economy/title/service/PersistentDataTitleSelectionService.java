package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.title.model.PlayerTitleSelection;
import com.splatage.wild_economy.title.model.TitleDisplayMode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PersistentDataTitleSelectionService implements TitleSelectionService {

    private final NamespacedKey modeKey;
    private final NamespacedKey selectedTitleKey;

    public PersistentDataTitleSelectionService(final String namespace) {
        final String normalizedNamespace = Objects.requireNonNull(namespace, "namespace")
            .trim()
            .toLowerCase(Locale.ROOT);
        this.modeKey = NamespacedKey.fromString(normalizedNamespace + ":title_display_mode");
        this.selectedTitleKey = NamespacedKey.fromString(normalizedNamespace + ":selected_title_key");
        if (this.modeKey == null || this.selectedTitleKey == null) {
            throw new IllegalArgumentException("Invalid namespace for title selection keys: " + namespace);
        }
    }

    @Override
    public PlayerTitleSelection getSelection(final Player player) {
        Objects.requireNonNull(player, "player");
        final PersistentDataContainer container = player.getPersistentDataContainer();
        final String storedMode = container.get(this.modeKey, PersistentDataType.STRING);
        final TitleDisplayMode mode = parseModeOrDefault(storedMode);
        final String selectedKey = container.get(this.selectedTitleKey, PersistentDataType.STRING);
        return new PlayerTitleSelection(mode, selectedKey);
    }

    @Override
    public void setSelection(final Player player, final PlayerTitleSelection selection) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(selection, "selection");
        final PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(this.modeKey, PersistentDataType.STRING, selection.displayMode().name());
        if (selection.selectedTitleKey() == null) {
            container.remove(this.selectedTitleKey);
        } else {
            container.set(this.selectedTitleKey, PersistentDataType.STRING, selection.selectedTitleKey());
        }
    }

    @Override
    public void clearSelection(final Player player) {
        Objects.requireNonNull(player, "player");
        final PersistentDataContainer container = player.getPersistentDataContainer();
        container.remove(this.modeKey);
        container.remove(this.selectedTitleKey);
    }

    @Override
    public PlayerTitleSelection getSelection(final UUID playerId) {
        throw new UnsupportedOperationException(
            "Offline title selection lookup is not implemented in the phase-1 PDC-backed service for " + playerId
        );
    }

    private static TitleDisplayMode parseModeOrDefault(final String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return TitleDisplayMode.OFF;
        }
        try {
            return TitleDisplayMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return TitleDisplayMode.OFF;
        }
    }
}
