package com.splatage.wild_economy.exchange.catalog;

import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.item.ExchangeItemCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ExchangeVariantWorthResolver {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    private static final BigDecimal THREE = BigDecimal.valueOf(3L);

    private final Map<ItemKey, ExchangeCatalogEntry> entries;
    private final RootValueLookup rootValueLookup;
    private final ExchangeItemCodec exchangeItemCodec;

    ExchangeVariantWorthResolver(
        final Map<ItemKey, ExchangeCatalogEntry> entries,
        final RootValueLookup rootValueLookup,
        final ExchangeItemCodec exchangeItemCodec
    ) {
        this.entries = Objects.requireNonNull(entries, "entries");
        this.rootValueLookup = Objects.requireNonNull(rootValueLookup, "rootValueLookup");
        this.exchangeItemCodec = Objects.requireNonNull(exchangeItemCodec, "exchangeItemCodec");
    }

    Optional<BigDecimal> resolveVariantBaseWorth(final ItemKey itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");

        final Optional<ExchangeItemCodec.FireworkDescriptor> fireworkDescriptor = this.exchangeItemCodec.fireworkDescriptor(itemKey);
        if (fireworkDescriptor.isPresent()) {
            return this.resolveFireworkBaseWorth(fireworkDescriptor.get());
        }

        final Optional<ExchangeItemCodec.PotionDescriptor> potionDescriptor = this.exchangeItemCodec.potionDescriptor(itemKey);
        if (potionDescriptor.isPresent()) {
            return this.resolvePotionBaseWorth(potionDescriptor.get());
        }

        return Optional.empty();
    }

    private Optional<BigDecimal> resolveFireworkBaseWorth(final ExchangeItemCodec.FireworkDescriptor descriptor) {
        final Optional<BigDecimal> paperWorth = this.lookupWorth("minecraft:paper");
        final Optional<BigDecimal> gunpowderWorth = this.lookupWorth("minecraft:gunpowder");
        if (paperWorth.isEmpty() || gunpowderWorth.isEmpty()) {
            return Optional.empty();
        }

        final int gunpowderCount = Math.max(0, descriptor.flightPower());
        return Optional.of(
            paperWorth.get()
                .add(gunpowderWorth.get().multiply(BigDecimal.valueOf(gunpowderCount)))
                .divide(THREE, MONEY_SCALE, MONEY_ROUNDING)
                .setScale(MONEY_SCALE, MONEY_ROUNDING)
        );
    }

    private Optional<BigDecimal> resolvePotionBaseWorth(final ExchangeItemCodec.PotionDescriptor descriptor) {
        final Optional<BigDecimal> regularPotionWorth = this.resolveRegularPotionWorth(descriptor.potionTypeToken(), descriptor.modeToken());
        if (regularPotionWorth.isEmpty()) {
            return Optional.empty();
        }

        return switch (descriptor.basePath()) {
            case "potion" -> regularPotionWorth;
            case "splash_potion" -> this.lookupWorth("minecraft:gunpowder")
                .map(regularPotionWorth.get()::add)
                .map(this::money);
            case "lingering_potion" -> this.lookupWorth("minecraft:gunpowder")
                .flatMap(gunpowderWorth -> this.lookupWorth("minecraft:dragon_breath")
                    .map(dragonBreathWorth -> regularPotionWorth.get().add(gunpowderWorth).add(dragonBreathWorth)))
                .map(this::money);
            default -> Optional.empty();
        };
    }

    private Optional<BigDecimal> resolveRegularPotionWorth(final String potionTypeToken, final String modeToken) {
        final Optional<BigDecimal> potionBaseWorth = this.lookupWorth("minecraft:potion");
        if (potionBaseWorth.isEmpty()) {
            return Optional.empty();
        }

        final Optional<BigDecimal> effectWorth = this.resolvePotionEffectWorth(normalizeToken(potionTypeToken));
        if (effectWorth.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal total = potionBaseWorth.get().add(effectWorth.get());
        switch (normalizeToken(modeToken)) {
            case "long" -> {
                final Optional<BigDecimal> redstoneWorth = this.lookupWorth("minecraft:redstone");
                if (redstoneWorth.isEmpty()) {
                    return Optional.empty();
                }
                total = total.add(redstoneWorth.get());
            }
            case "strong" -> {
                final Optional<BigDecimal> glowstoneWorth = this.lookupWorth("minecraft:glowstone_dust");
                if (glowstoneWorth.isEmpty()) {
                    return Optional.empty();
                }
                total = total.add(glowstoneWorth.get());
            }
            case "normal" -> {
            }
            default -> {
                return Optional.empty();
            }
        }

        return Optional.of(this.money(total));
    }

    private Optional<BigDecimal> resolvePotionEffectWorth(final String potionTypeToken) {
        return switch (potionTypeToken) {
            case "water", "mundane", "thick" -> Optional.of(ZERO);
            case "awkward" -> this.lookupWorth("minecraft:nether_wart").map(this::money);
            case "swiftness", "speed" -> this.lookupWorth("minecraft:sugar").map(this::money);
            case "leaping", "jump", "jump_boost" -> this.lookupWorth("minecraft:rabbit_foot").map(this::money);
            case "fire_resistance" -> this.lookupWorth("minecraft:magma_cream").map(this::money);
            case "water_breathing" -> this.lookupWorth("minecraft:pufferfish").map(this::money);
            case "healing", "instant_heal" -> this.lookupWorth("minecraft:glistering_melon_slice").map(this::money);
            case "poison" -> this.lookupWorth("minecraft:spider_eye").map(this::money);
            case "regeneration" -> this.lookupWorth("minecraft:ghast_tear").map(this::money);
            case "strength" -> this.lookupWorth("minecraft:blaze_powder").map(this::money);
            case "night_vision" -> this.lookupWorth("minecraft:golden_carrot").map(this::money);
            case "weakness" -> this.lookupWorth("minecraft:fermented_spider_eye").map(this::money);
            case "invisibility" -> this.resolvePotionEffectWorth("night_vision")
                .flatMap(nightVisionWorth -> this.lookupWorth("minecraft:fermented_spider_eye")
                    .map(ingredientWorth -> this.money(nightVisionWorth.add(ingredientWorth))));
            case "slowness" -> this.resolvePotionEffectWorth("swiftness")
                .flatMap(swiftnessWorth -> this.lookupWorth("minecraft:fermented_spider_eye")
                    .map(ingredientWorth -> this.money(swiftnessWorth.add(ingredientWorth))));
            case "harming", "instant_damage" -> this.resolvePotionEffectWorth("healing")
                .flatMap(healingWorth -> this.lookupWorth("minecraft:fermented_spider_eye")
                    .map(ingredientWorth -> this.money(healingWorth.add(ingredientWorth))));
            case "turtle_master" -> this.lookupWorth("minecraft:turtle_helmet").map(this::money);
            case "slow_falling" -> this.lookupWorth("minecraft:phantom_membrane").map(this::money);
            case "wind_charged" -> this.lookupWorth("minecraft:breeze_rod").map(this::money);
            case "oozing" -> this.lookupWorth("minecraft:slime_block").map(this::money);
            case "infested" -> this.lookupWorth("minecraft:stone").map(this::money);
            case "weaving" -> this.lookupWorth("minecraft:cobweb").map(this::money);
            default -> Optional.empty();
        };
    }

    private Optional<BigDecimal> lookupWorth(final String rawItemKey) {
        final ItemKey itemKey = new ItemKey(rawItemKey);
        final ExchangeCatalogEntry exactEntry = this.entries.get(itemKey);
        if (exactEntry != null && exactEntry.baseWorth() != null) {
            return Optional.of(this.money(exactEntry.baseWorth()));
        }
        return this.rootValueLookup.findRootValue(rawItemKey).map(this::money);
    }

    private BigDecimal money(final BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static String normalizeToken(final String token) {
        return token == null ? "" : token.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
