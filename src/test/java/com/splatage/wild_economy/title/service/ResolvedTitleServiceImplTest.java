package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.config.TitleSettingsConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.eligibility.TitleEligibilityEvaluator;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSource;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedTitleServiceImplTest {

    @Test
    void selectedEligibleTitleOverridesDefaultPlaceholder() {
        final UUID playerId = UUID.randomUUID();
        final TitleOption title = title("stormbound_galefoot", "Galefoot", 10);
        final InMemorySelectionService selectionService = new InMemorySelectionService();
        selectionService.setSelectedTitleKey(player(playerId), title.key());
        final ResolvedTitleServiceImpl service = new ResolvedTitleServiceImpl(
                new TitleSettingsConfig("%vault_group%", Map.of(title.key(), title)),
                allowAll(),
                selectionService,
                resolver(Map.of("%vault_group%", "VIP", "Galefoot", "Galefoot"))
        );

        service.warm(player(playerId));

        assertEquals("Galefoot", service.getResolvedTitle(offlinePlayer(playerId)).orElseThrow().text());
    }

    @Test
    void fallsBackToDefaultPlaceholderWhenSelectionMissing() {
        final UUID playerId = UUID.randomUUID();
        final TitleOption title = title("stormbound_galefoot", "Galefoot", 10);
        final ResolvedTitleServiceImpl service = new ResolvedTitleServiceImpl(
                new TitleSettingsConfig("%vault_group%", Map.of(title.key(), title)),
                allowAll(),
                new InMemorySelectionService(),
                resolver(Map.of("%vault_group%", "VIP"))
        );

        service.warm(player(playerId));

        assertEquals("VIP", service.getResolvedTitle(offlinePlayer(playerId)).orElseThrow().text());
        assertEquals("VIP", service.getDefaultTitleText(player(playerId)));
    }

    @Test
    void fallsBackToDefaultPlaceholderWhenSelectedTitleIsNoLongerEligible() {
        final UUID playerId = UUID.randomUUID();
        final TitleOption title = title("stormbound_galefoot", "Galefoot", 10);
        final InMemorySelectionService selectionService = new InMemorySelectionService();
        selectionService.setSelectedTitleKey(player(playerId), title.key());
        final ResolvedTitleServiceImpl service = new ResolvedTitleServiceImpl(
                new TitleSettingsConfig("%vault_group%", Map.of(title.key(), title)),
                deny(title.key()),
                selectionService,
                resolver(Map.of("%vault_group%", "VIP", "Galefoot", "Galefoot"))
        );

        service.warm(player(playerId));

        assertEquals("VIP", service.getResolvedTitle(offlinePlayer(playerId)).orElseThrow().text());
    }

    @Test
    void doesNotAutoSelectHighestEligibleTitleWhenNoSelectionExists() {
        final UUID playerId = UUID.randomUUID();
        final Map<String, TitleOption> titles = new LinkedHashMap<>();
        titles.put("stormbound_galefoot", title("stormbound_galefoot", "Galefoot", 10));
        titles.put("stormbound_crown", title("stormbound_crown", "Crown of Thunder", 100));
        final ResolvedTitleServiceImpl service = new ResolvedTitleServiceImpl(
                new TitleSettingsConfig("", titles),
                allowAll(),
                new InMemorySelectionService(),
                resolver(Map.of())
        );

        service.warm(player(playerId));

        assertTrue(service.getResolvedTitle(offlinePlayer(playerId)).isEmpty());
    }

    private static TitleOption title(final String key, final String text, final int priority) {
        return new TitleOption(
                key,
                text,
                text,
                "NAME_TAG",
                null,
                TitleSource.RELIC,
                "stormbound",
                1,
                priority,
                java.util.List.of(),
                java.util.List.of(),
                com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet.HIDE,
                null
        );
    }

    private static TitleEligibilityEvaluator allowAll() {
        return (player, option) -> StoreEligibilityResult.allowed();
    }

    private static TitleEligibilityEvaluator deny(final String keyToDeny) {
        return (player, option) -> option.key().equals(keyToDeny)
                ? StoreEligibilityResult.hidden()
                : StoreEligibilityResult.allowed();
    }

    private static TitleTextResolver resolver(final Map<String, String> replacements) {
        return (player, rawText) -> replacements.getOrDefault(rawText, rawText == null ? "" : rawText);
    }

    private static Player player(final UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "isOnline" -> true;
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player(" + playerId + ")";
                    default -> null;
                }
        );
    }

    private static OfflinePlayer offlinePlayer(final UUID playerId) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class[]{OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "isOnline" -> false;
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "OfflinePlayer(" + playerId + ")";
                    default -> null;
                }
        );
    }

    private static final class InMemorySelectionService implements TitleSelectionService {
        private final Map<UUID, String> selected = new java.util.HashMap<>();

        @Override
        public Optional<String> getSelectedTitleKey(final Player player) {
            return Optional.ofNullable(this.selected.get(player.getUniqueId()));
        }

        @Override
        public Optional<String> getSelectedTitleKey(final UUID playerId) {
            return Optional.ofNullable(this.selected.get(playerId));
        }

        @Override
        public void setSelectedTitleKey(final Player player, final String titleKey) {
            this.selected.put(player.getUniqueId(), titleKey);
        }

        @Override
        public void clearSelectedTitleKey(final Player player) {
            this.selected.remove(player.getUniqueId());
        }
    }
}
