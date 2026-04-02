package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.config.TitleSettingsConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.eligibility.TitleEligibilityEvaluator;
import com.splatage.wild_economy.title.model.ResolvedTitle;
import com.splatage.wild_economy.title.model.TitleOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class ResolvedTitleServiceImpl implements ResolvedTitleService {

    private final TitleSettingsConfig titleSettingsConfig;
    private final TitleEligibilityEvaluator titleEligibilityEvaluator;
    private final TitleSelectionService titleSelectionService;
    private final Map<UUID, ResolvedTitle> cache = new ConcurrentHashMap<>();

    public ResolvedTitleServiceImpl(
            final TitleSettingsConfig titleSettingsConfig,
            final TitleEligibilityEvaluator titleEligibilityEvaluator,
            final TitleSelectionService titleSelectionService
    ) {
        this.titleSettingsConfig = Objects.requireNonNull(titleSettingsConfig, "titleSettingsConfig");
        this.titleEligibilityEvaluator = Objects.requireNonNull(titleEligibilityEvaluator, "titleEligibilityEvaluator");
        this.titleSelectionService = Objects.requireNonNull(titleSelectionService, "titleSelectionService");
    }

    @Override
    public Optional<ResolvedTitle> getResolvedTitle(final OfflinePlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        final UUID playerId = player.getUniqueId();
        final Player onlinePlayer = player.isOnline() ? Bukkit.getPlayer(playerId) : null;
        if (onlinePlayer != null) {
            final ResolvedTitle resolved = this.resolve(onlinePlayer);
            return resolved.text().isBlank() ? Optional.empty() : Optional.of(resolved);
        }
        final ResolvedTitle cached = this.cache.get(playerId);
        if (cached == null) {
            return Optional.empty();
        }
        return cached.text().isBlank() ? Optional.empty() : Optional.of(cached);
    }

    @Override
    public void warm(final Player player) {
        this.cache.put(player.getUniqueId(), this.resolve(player));
    }

    @Override
    public void invalidate(final UUID playerId) {
        this.cache.remove(playerId);
    }

    private ResolvedTitle resolve(final Player player) {
        final Optional<String> selectedTitleKey = this.titleSelectionService.getSelectedTitleKey(player);
        if (selectedTitleKey.isPresent()) {
            final TitleOption selected = this.titleSettingsConfig.titles().get(selectedTitleKey.get());
            if (selected != null) {
                final StoreEligibilityResult evaluation = this.titleEligibilityEvaluator.evaluate(player, selected);
                if (evaluation.visible() && evaluation.acquirable()) {
                    return new ResolvedTitle(selected.key(), selected.titleText(), selected.source(), selected.family());
                }
            }
        }

        for (final TitleOption option : this.titleSettingsConfig.orderedTitles()) {
            final StoreEligibilityResult evaluation = this.titleEligibilityEvaluator.evaluate(player, option);
            if (evaluation.visible() && evaluation.acquirable()) {
                return new ResolvedTitle(option.key(), option.titleText(), option.source(), option.family());
            }
        }
        return ResolvedTitle.empty();
    }
}
