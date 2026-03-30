package com.splatage.wild_economy.store.eligibility;

import com.splatage.wild_economy.store.model.StoreRequirementType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

public final class StoreRawStatisticWhitelist {

    private static final EnumSet<Statistic> SIMPLE_STATS = EnumSet.of(
        Statistic.PLAY_ONE_MINUTE,
        Statistic.WALK_ONE_CM,
        Statistic.SPRINT_ONE_CM,
        Statistic.SWIM_ONE_CM,
        Statistic.AVIATE_ONE_CM,
        Statistic.JUMP,
        Statistic.MOB_KILLS,
        Statistic.PLAYER_KILLS,
        Statistic.ANIMALS_BRED,
        Statistic.FISH_CAUGHT,
        Statistic.TALKED_TO_VILLAGER,
        Statistic.TRADED_WITH_VILLAGER,
        Statistic.RAID_TRIGGER,
        Statistic.RAID_WIN,
        Statistic.DEATHS,
        Statistic.TIME_SINCE_DEATH,
        Statistic.TIME_SINCE_REST,
        Statistic.TARGET_HIT
    );

    private static final EnumSet<Statistic> MATERIAL_STATS = EnumSet.of(
        Statistic.MINE_BLOCK,
        Statistic.CRAFT_ITEM,
        Statistic.USE_ITEM,
        Statistic.BREAK_ITEM,
        Statistic.PICKUP,
        Statistic.DROP
    );

    private static final EnumSet<Statistic> ENTITY_STATS = EnumSet.of(
        Statistic.KILL_ENTITY,
        Statistic.ENTITY_KILLED_BY
    );

    private StoreRawStatisticWhitelist() {
    }

    public static Statistic validateRequirement(
        final StoreRequirementType requirementType,
        final String rawStatistic,
        final String rawMaterial,
        final String rawEntityType,
        final String context
    ) {
        Objects.requireNonNull(requirementType, "requirementType");
        final Statistic statistic = parseStatistic(rawStatistic, context);
        return switch (requirementType) {
            case STATISTIC -> validateSimple(statistic, context);
            case STATISTIC_MATERIAL -> {
                validateMaterial(statistic, rawMaterial, context);
                yield statistic;
            }
            case STATISTIC_ENTITY -> {
                validateEntity(statistic, rawEntityType, context);
                yield statistic;
            }
            default -> throw new IllegalArgumentException("Unsupported raw statistic requirement type: " + requirementType);
        };
    }

    public static Statistic validateSimple(final String rawStatistic, final String context) {
        return validateSimple(parseStatistic(rawStatistic, context), context);
    }

    public static Statistic validateSimple(final Statistic statistic, final String context) {
        if (!SIMPLE_STATS.contains(statistic) || statistic.getType() != Statistic.Type.UNTYPED) {
            throw new IllegalStateException(context + " uses unsupported raw statistic '" + statistic.name() + "'");
        }
        return statistic;
    }

    public static Material validateMaterial(final String rawStatistic, final String rawMaterial, final String context) {
        final Statistic statistic = parseStatistic(rawStatistic, context);
        return validateMaterial(statistic, rawMaterial, context);
    }

    public static Material validateMaterial(final Statistic statistic, final String rawMaterial, final String context) {
        if (!MATERIAL_STATS.contains(statistic)) {
            throw new IllegalStateException(context + " uses unsupported material-qualified raw statistic '" + statistic.name() + "'");
        }
        if (statistic.getType() != Statistic.Type.BLOCK && statistic.getType() != Statistic.Type.ITEM) {
            throw new IllegalStateException(context + " uses non-material statistic '" + statistic.name() + "' with a material qualifier");
        }
        final Material material = Material.matchMaterial(Objects.requireNonNull(rawMaterial, "rawMaterial"));
        if (material == null) {
            throw new IllegalStateException(context + " uses unknown material '" + rawMaterial + "'");
        }
        return material;
    }

    public static EntityType validateEntity(final String rawStatistic, final String rawEntityType, final String context) {
        final Statistic statistic = parseStatistic(rawStatistic, context);
        return validateEntity(statistic, rawEntityType, context);
    }

    public static EntityType validateEntity(final Statistic statistic, final String rawEntityType, final String context) {
        if (!ENTITY_STATS.contains(statistic) || statistic.getType() != Statistic.Type.ENTITY) {
            throw new IllegalStateException(context + " uses unsupported entity-qualified raw statistic '" + statistic.name() + "'");
        }
        try {
            return EntityType.valueOf(Objects.requireNonNull(rawEntityType, "rawEntityType").trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException(context + " uses unknown entity type '" + rawEntityType + "'", exception);
        }
    }

    private static Statistic parseStatistic(final String rawStatistic, final String context) {
        try {
            return Statistic.valueOf(Objects.requireNonNull(rawStatistic, "rawStatistic").trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException(context + " uses unknown raw statistic '" + rawStatistic + "'", exception);
        }
    }
}
