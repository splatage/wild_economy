package com.splatage.wild_economy.title.service;

import com.splatage.wild_economy.config.TitleSettingsConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.eligibility.TitleEligibilityEvaluator;
import com.splatage.wild_economy.title.model.PlayerTitleSelection;
import com.splatage.wild_economy.title.model.ResolvedTitle;
import com.splatage.wild_economy.title.model.TitleDisplayMode;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSource;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.bukkit.entity.Player;

public final class ResolvedTitleServiceImpl implements ResolvedTitleService {

    private static final Comparator<TitleOption> RELIC_PRIORITY =
        Comparator.comparing(TitleOption::tier, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingInt(TitleOption::priority)
            .reversed();

    private static final Comparator<TitleOption> PRIORITY_ORDER =
        Comparator.comparingInt(TitleOption::priority).reversed()
            .thenComparing(TitleOption::displayName);

    private final TitleSettingsConfig titleSettingsConfig;
    private final TitleSelectionService titleSelectionService;
    private final TitleEligibilityEvaluator titleEligibilityEvaluator;
    private final Map<UUID, ResolvedTitle> cache = new ConcurrentHashMap<>();

    public ResolvedTitleServiceImpl(
        final TitleSettingsConfig titleSettingsConfig,
        final TitleSelectionService titleSelectionService,
        final TitleEligibilityEvaluator titleEligibilityEvaluator
    ) {
        this.titleSettingsConfig = Objects.requireNonNull(titleSettingsConfig, "titleSettingsConfig");
        this.titleSelectionService = Objects.requireNonNull(titleSelectionService, "titleSelectionService");
        this.titleEligibilityEvaluator = Objects.requireNonNull(titleEligibilityEvaluator, "titleEligibilityEvaluator");
    }

    @Override
    public ResolvedTitle resolve(final Player player) {
        Objects.requireNonNull(player, "player");
        final ResolvedTitle resolved = this.resolveFresh(player);
        this.cache.put(player.getUniqueId(), resolved);
        return resolved;
    }

    @Override
    public ResolvedTitle getCached(final UUID playerId) {
        return this.cache.getOrDefault(Objects.requireNonNull(playerId, "playerId"), ResolvedTitle.EMPTY);
    }

    @Override
    public void invalidate(final UUID playerId) {
        this.cache.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public void invalidateAll() {
        this.cache.clear();
    }

    private ResolvedTitle resolveFresh(final Player player) {
        final PlayerTitleSelection selection = this.titleSelectionService.getSelection(player);

        return switch (selection.displayMode()) {
            case OFF -> ResolvedTitle.EMPTY;
            case MANUAL -> this.resolveManual(player, selection.selectedTitleKey());
            case AUTO_HIGHEST_PRIORITY -> this.resolveAuto(player, PRIORITY_ORDER, null);
            case AUTO_HIGHEST_RELIC -> this.resolveAuto(player, RELIC_PRIORITY, TitleSource.RELIC);
            case AUTO_HIGHEST_COMMERCE -> this.resolveAuto(player, PRIORITY_ORDER, null, TitleSource.COMMERCE_MILESTONE, TitleSource.COMMERCE_CROWN);
            case AUTO_CURRENT_CROWN -> this.resolveAuto(player, PRIORITY_ORDER, null, TitleSource.COMMERCE_CROWN);
        };
    }

    private ResolvedTitle resolveManual(final Player player, final String selectedTitleKey) {
        if (selectedTitleKey == null) {
            return ResolvedTitle.EMPTY;
        }
        final TitleOption selected = this.titleSettingsConfig.options().get(selectedTitleKey);
        if (selected == null) {
            return ResolvedTitle.EMPTY;
        }
        final StoreEligibilityResult eligibility = this.titleEligibilityEvaluator.evaluate(player, selected);
        return eligibility.acquirable() ? asResolved(selected) : ResolvedTitle.EMPTY;
    }

    private ResolvedTitle resolveAuto(
        final Player player,
        final Comparator<TitleOption> ordering,
        final TitleSource requiredSource,
        final TitleSource... allowedSources
    ) {
        final Stream<TitleOption> stream = this.titleSettingsConfig.options().values().stream()
            .filter(option -> requiredSource == null || option.source() == requiredSource)
            .filter(option -> {
                if (allowedSources == null || allowedSources.length == 0) {
                    return true;
                }
                for (final TitleSource allowed : allowedSources) {
                    if (option.source() == allowed) {
                        return true;
                    }
                }
                return false;
            })
            .filter(option -> this.titleEligibilityEvaluator.evaluate(player, option).acquirable())
            .sorted(ordering);

        return stream.findFirst().map(ResolvedTitleServiceImpl::asResolved).orElse(ResolvedTitle.EMPTY);
    }

    private static ResolvedTitle asResolved(final TitleOption option) {
        return new ResolvedTitle(
            option.key(),
            option.titleText(),
            option.source(),
            option.family(),
            option.tier()
        );
    }
}
