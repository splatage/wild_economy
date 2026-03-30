package com.splatage.wild_economy.store.eligibility;

import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StoreRequirement;
import com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet;
import com.splatage.wild_economy.store.state.StoreEntitlementRecord;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public final class StoreEligibilityServiceImpl implements StoreEligibilityService {

    private static final Pattern TIERED_KEY_PATTERN = Pattern.compile("^(.*?)(\\.)(0*)(\\d+)$");

    private final StoreRuntimeStateService storeRuntimeStateService;
    private final long tieredTrackPurchaseCooldownSeconds;

    public StoreEligibilityServiceImpl(
        final StoreRuntimeStateService storeRuntimeStateService,
        final long tieredTrackPurchaseCooldownSeconds
    ) {
        this.storeRuntimeStateService = Objects.requireNonNull(storeRuntimeStateService, "storeRuntimeStateService");
        this.tieredTrackPurchaseCooldownSeconds = Math.max(0L, tieredTrackPurchaseCooldownSeconds);
    }

    @Override
    public StoreEligibilityResult evaluateCategory(final Player player, final StoreCategory category) {
        return this.evaluateRequirements(
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
                        List.of("Owned: Yes"),
                        product.lockedMessage()
                );
            }
            if (ownershipState == StoreOwnershipState.LOADING) {
                return this.lockedFor(product.visibilityWhenUnmet(), product.lockedMessage(), "Store data is still loading.", List.of("Owned: Loading..."));
            }
            if (ownershipState == StoreOwnershipState.LOAD_FAILED) {
                return this.lockedFor(product.visibilityWhenUnmet(), product.lockedMessage(), "Store data could not be loaded right now.", List.of("Owned: Unavailable"));
            }
        }

        final StoreEligibilityResult base = this.evaluateRequirements(
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

        final StoreOwnershipState priorState = this.storeRuntimeStateService.getOwnershipState(playerId, tierInfo.previousTierKey());
        if (priorState != StoreOwnershipState.OWNED) {
            return this.lockedFor(
                    product.visibilityWhenUnmet(),
                    product.lockedMessage(),
                    "You must unlock the previous tier first.",
                    List.of("Previous tier required: " + tierInfo.previousTierKey())
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
                    List.of("Cooldown remaining: " + this.formatDurationSeconds(remaining))
            );
        }

        return base;
    }

    private StoreEligibilityResult evaluateRequirements(
        final Player player,
        final List<StoreRequirement> requirements,
        final StoreVisibilityWhenUnmet visibilityWhenUnmet,
        final String lockedMessage
    ) {
        final List<String> progressLines = new ArrayList<>();
        String blockedMessage = null;
        for (final StoreRequirement requirement : requirements) {
            final RequirementEvaluation evaluation = this.evaluateRequirement(player, requirement);
            progressLines.add(evaluation.progressLine());
            if (!evaluation.satisfied() && blockedMessage == null) {
                blockedMessage = evaluation.blockedMessage();
            }
        }

        if (blockedMessage == null) {
            return StoreEligibilityResult.allowed();
        }
        return this.lockedFor(visibilityWhenUnmet, lockedMessage, blockedMessage, progressLines);
    }

    private StoreEligibilityResult lockedFor(
        final StoreVisibilityWhenUnmet visibilityWhenUnmet,
        final String lockedMessage,
        final String blockedMessage,
        final List<String> progressLines
    ) {
        if (visibilityWhenUnmet == StoreVisibilityWhenUnmet.HIDE) {
            return StoreEligibilityResult.hidden();
        }
        return StoreEligibilityResult.locked(blockedMessage, progressLines, lockedMessage);
    }

    private RequirementEvaluation evaluateRequirement(final Player player, final StoreRequirement requirement) {
        return switch (requirement.type()) {
            case ENTITLEMENT -> this.evaluateEntitlementRequirement(player.getUniqueId(), requirement);
            case PERMISSION -> this.evaluatePermissionRequirement(player, requirement);
            case STATISTIC -> this.evaluateStatisticRequirement(player, requirement);
            case STATISTIC_MATERIAL -> this.evaluateStatisticMaterialRequirement(player, requirement);
        };
    }

    private RequirementEvaluation evaluateEntitlementRequirement(final UUID playerId, final StoreRequirement requirement) {
        final StoreOwnershipState ownershipState = this.storeRuntimeStateService.getOwnershipState(playerId, requirement.key());
        return switch (ownershipState) {
            case OWNED -> new RequirementEvaluation(true, "Requires unlock: " + requirement.key() + " (met)", null);
            case LOADING -> new RequirementEvaluation(false, "Requires unlock: " + requirement.key() + " (loading)", "Store data is still loading.");
            case LOAD_FAILED -> new RequirementEvaluation(false, "Requires unlock: " + requirement.key() + " (unavailable)", "Store data could not be loaded right now.");
            case NOT_OWNED -> new RequirementEvaluation(false, "Requires unlock: " + requirement.key() + " (missing)", "You do not yet own the required unlock.");
        };
    }

    private RequirementEvaluation evaluatePermissionRequirement(final Player player, final StoreRequirement requirement) {
        final boolean allowed = player.hasPermission(requirement.permissionNode());
        return new RequirementEvaluation(
                allowed,
                "Permission: " + requirement.permissionNode() + (allowed ? " (met)" : " (missing)"),
                allowed ? null : "You do not have the required permission."
        );
    }

    private RequirementEvaluation evaluateStatisticRequirement(final Player player, final StoreRequirement requirement) {
        final Statistic statistic = Statistic.valueOf(requirement.statistic().toUpperCase(Locale.ROOT));
        final int current = player.getStatistic(statistic);
        return new RequirementEvaluation(
                current >= requirement.minimum(),
                this.prettyEnumName(statistic.name()) + ": " + current + " / " + requirement.minimum(),
                current >= requirement.minimum() ? null : "You have not yet reached the required progress."
        );
    }

    private RequirementEvaluation evaluateStatisticMaterialRequirement(final Player player, final StoreRequirement requirement) {
        final Statistic statistic = Statistic.valueOf(requirement.statistic().toUpperCase(Locale.ROOT));
        final Material material = Material.matchMaterial(requirement.material());
        if (material == null) {
            throw new IllegalStateException("Unknown material in Store requirement: " + requirement.material());
        }
        final int current = player.getStatistic(statistic, material);
        return new RequirementEvaluation(
                current >= requirement.minimum(),
                this.prettyEnumName(statistic.name()) + " " + this.prettyEnumName(material.name()) + ": " + current + " / " + requirement.minimum(),
                current >= requirement.minimum() ? null : "You have not yet reached the required progress."
        );
    }

    private String prettyEnumName(final String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
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

    private record RequirementEvaluation(boolean satisfied, String progressLine, String blockedMessage) {
    }

    private record TierInfo(String previousTierKey) {
    }
}
