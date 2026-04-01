package com.splatage.wild_economy.store.eligibility;

import com.splatage.wild_economy.config.StoreProductsConfig;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.model.StoreRequirement;
import com.splatage.wild_economy.store.model.StoreVisibilityWhenUnmet;
import com.splatage.wild_economy.store.progress.StoreProgressService;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import com.splatage.wild_economy.store.state.StoreRuntimeStateService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class StoreRequirementGateServiceImpl implements StoreRequirementGateService {

    private final StoreRuntimeStateService storeRuntimeStateService;
    private final StoreProgressService storeProgressService;
    private final Map<String, String> entitlementDisplayNames;

    public StoreRequirementGateServiceImpl(
            final StoreRuntimeStateService storeRuntimeStateService,
            final StoreProgressService storeProgressService,
            final StoreProductsConfig storeProductsConfig
    ) {
        this.storeRuntimeStateService = Objects.requireNonNull(storeRuntimeStateService, "storeRuntimeStateService");
        this.storeProgressService = Objects.requireNonNull(storeProgressService, "storeProgressService");
        this.entitlementDisplayNames = this.buildEntitlementDisplayNames(Objects.requireNonNull(storeProductsConfig, "storeProductsConfig"));
    }

    @Override
    public StoreEligibilityResult evaluate(
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
            case STATISTIC_ENTITY -> this.evaluateStatisticEntityRequirement(player, requirement);
            case ADVANCEMENT -> this.evaluateAdvancementRequirement(player, requirement);
            case CUSTOM_COUNTER -> this.evaluateCustomCounterRequirement(player, requirement);
        };
    }

    private RequirementEvaluation evaluateEntitlementRequirement(final UUID playerId, final StoreRequirement requirement) {
        final String entitlementLabel = this.resolveEntitlementDisplayName(requirement.key());
        final String requirementLine = "Requires: " + entitlementLabel;
        final StoreOwnershipState ownershipState = this.storeRuntimeStateService.getOwnershipState(playerId, requirement.key());
        return switch (ownershipState) {
            case OWNED -> new RequirementEvaluation(true, requirementLine, null);
            case LOADING -> new RequirementEvaluation(false, requirementLine + " (checking...)", "Store data is still loading.");
            case LOAD_FAILED -> new RequirementEvaluation(false, requirementLine + " (unavailable)", "Store data could not be loaded right now.");
            case NOT_OWNED -> new RequirementEvaluation(false, requirementLine, "Unlock " + entitlementLabel + " first.");
        };
    }

    private RequirementEvaluation evaluatePermissionRequirement(final Player player, final StoreRequirement requirement) {
        final boolean allowed = player.hasPermission(requirement.permissionNode());
        return new RequirementEvaluation(
                allowed,
                "Requires access: " + this.prettyPermission(requirement.permissionNode()),
                allowed ? null : "You do not yet have access to this option."
        );
    }

    private RequirementEvaluation evaluateStatisticRequirement(final Player player, final StoreRequirement requirement) {
        final Statistic statistic = StoreRawStatisticWhitelist.validateSimple(requirement.statistic(), "Eligibility requirement");
        final int current = player.getStatistic(statistic);
        return new RequirementEvaluation(
                current >= requirement.minimum(),
                this.prettyWords(statistic.name()) + ": " + current + " / " + requirement.minimum(),
                current >= requirement.minimum() ? null : "You have not yet reached the required progress."
        );
    }

    private RequirementEvaluation evaluateStatisticMaterialRequirement(final Player player, final StoreRequirement requirement) {
        final Statistic statistic = StoreRawStatisticWhitelist.validateRequirement(
                requirement.type(),
                requirement.statistic(),
                requirement.material(),
                requirement.entityType(),
                "Eligibility requirement"
        );
        final Material material = StoreRawStatisticWhitelist.validateMaterial(statistic, requirement.material(), "Eligibility requirement");
        final int current = player.getStatistic(statistic, material);
        return new RequirementEvaluation(
                current >= requirement.minimum(),
                this.prettyWords(statistic.name()) + " " + this.prettyWords(material.name()) + ": " + current + " / " + requirement.minimum(),
                current >= requirement.minimum() ? null : "You have not yet reached the required progress."
        );
    }

    private RequirementEvaluation evaluateStatisticEntityRequirement(final Player player, final StoreRequirement requirement) {
        final Statistic statistic = StoreRawStatisticWhitelist.validateRequirement(
                requirement.type(),
                requirement.statistic(),
                requirement.material(),
                requirement.entityType(),
                "Eligibility requirement"
        );
        final EntityType entityType = StoreRawStatisticWhitelist.validateEntity(statistic, requirement.entityType(), "Eligibility requirement");
        final int current = player.getStatistic(statistic, entityType);
        return new RequirementEvaluation(
                current >= requirement.minimum(),
                this.prettyWords(statistic.name()) + " " + this.prettyWords(entityType.name()) + ": " + current + " / " + requirement.minimum(),
                current >= requirement.minimum() ? null : "You have not yet reached the required progress."
        );
    }

    private RequirementEvaluation evaluateAdvancementRequirement(final Player player, final StoreRequirement requirement) {
        final boolean completed = this.storeProgressService.hasAdvancement(player, requirement.key());
        return new RequirementEvaluation(
                completed,
                "Requires advancement: " + this.prettyAdvancement(requirement.key()),
                completed ? null : "You have not yet completed the required advancement."
        );
    }

    private RequirementEvaluation evaluateCustomCounterRequirement(final Player player, final StoreRequirement requirement) {
        final long current = this.storeProgressService.getCustomCounter(player, requirement.key());
        return new RequirementEvaluation(
                current >= requirement.minimum(),
                this.prettyWords(requirement.key()) + ": " + current + " / " + requirement.minimum(),
                current >= requirement.minimum() ? null : "You have not yet reached the required progress."
        );
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

    private String resolveEntitlementDisplayName(final String entitlementKey) {
        final String displayName = this.entitlementDisplayNames.get(entitlementKey);
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return this.prettyWords(entitlementKey);
    }

    private String prettyPermission(final String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return "Access";
        }
        final int lastDot = permissionNode.lastIndexOf('.');
        final String segment = lastDot >= 0 ? permissionNode.substring(lastDot + 1) : permissionNode;
        return this.prettyWords(segment);
    }

    private String prettyAdvancement(final String advancementKey) {
        if (advancementKey == null || advancementKey.isBlank()) {
            return "Advancement";
        }
        final int lastSlash = advancementKey.lastIndexOf('/');
        final String tail = lastSlash >= 0 ? advancementKey.substring(lastSlash + 1) : advancementKey;
        final int lastColon = tail.lastIndexOf(':');
        final String segment = lastColon >= 0 ? tail.substring(lastColon + 1) : tail;
        return this.prettyWords(segment);
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

    private record RequirementEvaluation(boolean satisfied, String progressLine, String blockedMessage) {
    }
}
