package com.splatage.wild_economy.store.eligibility;

import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.progress.StoreProgressService;
import com.splatage.wild_economy.store.state.StoreEntitlementRecord;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

public final class StoreEligibilityServiceImpl implements StoreEligibilityService {

    private static final Pattern TIERED_KEY_PATTERN = Pattern.compile("^(.*?)(\\.)(0*)(\\d+)$");

    private final StoreRuntimeStateService storeRuntimeStateService;
    private final StoreRequirementGateService requirementGateService;
    private final Map<String, String> entitlementDisplayNames;
    private final long tieredTrackPurchaseCooldownSeconds;

    public StoreEligibilityServiceImpl(
        final StoreRuntimeStateService storeRuntimeStateService,
        final StoreProgressService storeProgressService,
        final long tieredTrackPurchaseCooldownSeconds
    ) {
        this(
                storeRuntimeStateService,
                new StoreRequirementGateServiceImpl(storeRuntimeStateService, storeProgressService, StoreProductsConfig.EMPTY),
                StoreProductsConfig.EMPTY,
                tieredTrackPurchaseCooldownSeconds
        );
    }

    public StoreEligibilityServiceImpl(
        final StoreRuntimeStateService storeRuntimeStateService,
        final StoreProgressService storeProgressService,
        final StoreProductsConfig storeProductsConfig,
        final long tieredTrackPurchaseCooldownSeconds
    ) {
        this(
                storeRuntimeStateService,
                new StoreRequirementGateServiceImpl(storeRuntimeStateService, storeProgressService, storeProductsConfig),
                storeProductsConfig,
                tieredTrackPurchaseCooldownSeconds
        );
    }

    public StoreEligibilityServiceImpl(
        final StoreRuntimeStateService storeRuntimeStateService,
        final StoreRequirementGateService requirementGateService,
        final long tieredTrackPurchaseCooldownSeconds
    ) {
        this(storeRuntimeStateService, requirementGateService, StoreProductsConfig.EMPTY, tieredTrackPurchaseCooldownSeconds);
    }

    public StoreEligibilityServiceImpl(
        final StoreRuntimeStateService storeRuntimeStateService,
        final StoreRequirementGateService requirementGateService,
        final StoreProductsConfig storeProductsConfig,
        final long tieredTrackPurchaseCooldownSeconds
    ) {
        this.storeRuntimeStateService = Objects.requireNonNull(storeRuntimeStateService, "storeRuntimeStateService");
        this.requirementGateService = Objects.requireNonNull(requirementGateService, "requirementGateService");
        this.entitlementDisplayNames = this.buildEntitlementDisplayNames(Objects.requireNonNull(storeProductsConfig, "storeProductsConfig"));
        this.tieredTrackPurchaseCooldownSeconds = Math.max(0L, tieredTrackPurchaseCooldownSeconds);
    }

    @Override
    public StoreEligibilityResult evaluateCategory(final Player player, final StoreCategory category) {
        return this.requirementGateService.evaluate(
                player,
                category.requirements(),
                category.visibilityWhenUnmet(),
                category.lockedMessage()
        );
    }

    @Override
    public StoreEligibilityResult evaluateProduct(final Player player, final StoreProduct product) {
        final UUID playerId = player.getUniqueId();
        if (product.type() == StoreProductType.PERMANENT_UNLOCK) {
            final StoreOwnershipState ownershipState = this.storeRuntimeStateService.getOwnershipState(playerId, product.entitlementKey());
            if (ownershipState == StoreOwnershipState.OWNED) {
                return StoreEligibilityResult.locked(
                        "You already own this unlock.",
                        List.of("Ownership: Unlocked"),
                        product.lockedMessage()
                );
            }
            if (ownershipState == StoreOwnershipState.LOADING) {
                return this.lockedFor(
                        product.visibilityWhenUnmet(),
                        product.lockedMessage(),
                        "Store data is still loading.",
                        List.of("Ownership: Checking...")
                );
            }
            if (ownershipState == StoreOwnershipState.LOAD_FAILED) {
                return this.lockedFor(
                        product.visibilityWhenUnmet(),
                        product.lockedMessage(),
                        "Store data could not be loaded right now.",
                        List.of("Ownership: Unavailable")
                );
            }
        }

        final StoreEligibilityResult base = this.requirementGateService.evaluate(
                player,
                product.requirements(),
                product.visibilityWhenUnmet(),
                product.lockedMessage()
        );
        if (!base.acquirable()) {
            return base;
        }

        if (product.type() != StoreProductType.PERMANENT_UNLOCK) {
            return base;
        }

        final TierInfo tierInfo = this.parseTierInfo(product.entitlementKey());
        if (tierInfo == null) {
            return base;
        }

        final String previousTierLabel = this.resolveEntitlementDisplayName(tierInfo.previousTierKey());
        final StoreOwnershipState priorState = this.storeRuntimeStateService.getOwnershipState(playerId, tierInfo.previousTierKey());
        if (priorState != StoreOwnershipState.OWNED) {
            final String previousTierMessage = "Unlock " + previousTierLabel + " first.";
            return this.lockedFor(
                    product.visibilityWhenUnmet(),
                    product.lockedMessage(),
                    previousTierMessage,
                    List.of(previousTierMessage)
            );
        }

        if (this.tieredTrackPurchaseCooldownSeconds <= 0L) {
            return base;
        }

        final StoreEntitlementRecord previousTierRecord = this.storeRuntimeStateService.getEntitlementRecord(playerId, tierInfo.previousTierKey());
        if (previousTierRecord == null) {
            return base;
        }

        final long now = Instant.now().getEpochSecond();
        final long earliestAllowed = previousTierRecord.grantedAtEpochSecond() + this.tieredTrackPurchaseCooldownSeconds;
        if (earliestAllowed > now) {
            final long remaining = earliestAllowed - now;
            return this.lockedFor(
                    product.visibilityWhenUnmet(),
                    product.lockedMessage(),
                    "This track is cooling down before the next tier can be purchased.",
                    List.of("Next tier available in: " + this.formatDurationSeconds(remaining))
            );
        }

        return base;
    }

    private StoreEligibilityResult lockedFor(
            final com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet visibilityWhenUnmet,
            final String lockedMessage,
            final String blockedMessage,
            final List<String> progressLines
    ) {
        if (visibilityWhenUnmet == com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet.HIDE) {
            return StoreEligibilityResult.hidden();
        }
        return StoreEligibilityResult.locked(blockedMessage, progressLines, lockedMessage);
    }

    private String resolveEntitlementDisplayName(final String entitlementKey) {
        if (entitlementKey == null || entitlementKey.isBlank()) {
            return "previous tier";
        }
        final String displayName = this.entitlementDisplayNames.get(entitlementKey);
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return this.prettyWords(entitlementKey);
    }

    private Map<String, String> buildEntitlementDisplayNames(final StoreProductsConfig storeProductsConfig) {
        final Map<String, String> displayNames = new HashMap<>();
        for (final StoreProduct product : storeProductsConfig.products().values()) {
            if (product.type() != StoreProductType.PERMANENT_UNLOCK) {
                continue;
            }
            final String entitlementKey = product.entitlementKey();
            if (entitlementKey == null || entitlementKey.isBlank()) {
                continue;
            }
            displayNames.putIfAbsent(entitlementKey, product.displayName());
        }
        return Map.copyOf(displayNames);
    }

    private String formatDurationSeconds(final long seconds) {
        final long hours = seconds / 3600L;
        final long minutes = (seconds % 3600L) / 60L;
        final long remainingSeconds = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }


    private String prettyWords(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        final String[] parts = raw.replace(':', ' ')
                .replace('/', ' ')
                .replace('.', ' ')
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .split("\\s+");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private TierInfo parseTierInfo(final String entitlementKey) {
        if (entitlementKey == null || entitlementKey.isBlank()) {
            return null;
        }
        final Matcher matcher = TIERED_KEY_PATTERN.matcher(entitlementKey);
        if (!matcher.matches()) {
            return null;
        }
        final long tierNumber = Long.parseLong(matcher.group(4));
        if (tierNumber <= 1L) {
            return null;
        }
        final String baseKey = matcher.group(1);
        final String separator = matcher.group(2);
        final String zeroPadding = matcher.group(3);
        final long previousTierNumber = tierNumber - 1L;
        final String previousTierKey = baseKey + separator + zeroPadding + previousTierNumber;
        return new TierInfo(previousTierKey);
    }

    private record TierInfo(String previousTierKey) {
    }
}
