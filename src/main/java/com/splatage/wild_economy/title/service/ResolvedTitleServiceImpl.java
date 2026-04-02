package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.config.TitleSettingsConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.eligibility.TitleEligibilityEvaluator;
import com.splatage.wild_economy.title.model.ResolvedTitle;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSource;
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
    private final TitleTextResolver titleTextResolver;
    private final Map<UUID, ResolvedTitle> cache = new ConcurrentHashMap<>();

    public ResolvedTitleServiceImpl(
            final TitleSettingsConfig titleSettingsConfig,
            final TitleEligibilityEvaluator titleEligibilityEvaluator,
            final TitleSelectionService titleSelectionService,
            final TitleTextResolver titleTextResolver
    ) {
        this.titleSettingsConfig = Objects.requireNonNull(titleSettingsConfig, "titleSettingsConfig");
        this.titleEligibilityEvaluator = Objects.requireNonNull(titleEligibilityEvaluator, "titleEligibilityEvaluator");
        this.titleSelectionService = Objects.requireNonNull(titleSelectionService, "titleSelectionService");
        this.titleTextResolver = Objects.requireNonNull(titleTextResolver, "titleTextResolver");
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
    public String getDefaultTitleText(final Player player) {
        if (player == null) {
            return "";
        }
        return this.resolveDefaultTitle(player).text();
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
                    return this.resolveSelectedTitle(player, selected);
                }
            }
        }
        return this.resolveDefaultTitle(player);
    }

    private ResolvedTitle resolveSelectedTitle(final Player player, final TitleOption option) {
        return new ResolvedTitle(
                option.key(),
                this.titleTextResolver.resolve(player, option.titleText()),
                option.source(),
                option.family()
        );
    }

    private ResolvedTitle resolveDefaultTitle(final Player player) {
        final String text = this.titleTextResolver.resolve(player, this.titleSettingsConfig.defaultTitlePlaceholder());
        if (text.isBlank()) {
            return ResolvedTitle.empty();
        }
        return new ResolvedTitle("", text, TitleSource.DEFAULT, "");
    }
}
