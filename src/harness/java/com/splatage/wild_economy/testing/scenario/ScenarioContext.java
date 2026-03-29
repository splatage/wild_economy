package com.splatage.wild_economy.testing.scenario;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.exchange.activity.MarketActivityCategory;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.testing.seed.SeedPlan;
import com.splatage.wild_economy.testing.support.SeedPlayer;
import com.splatage.wild_economy.testing.support.SeedPlayerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public record ScenarioContext(
        HarnessBootstrap.HarnessComponents components,
        SeedPlan seedPlan,
        List<SeedPlayer> players,
        List<ExchangeCatalogEntry> browseEntries,
        List<ExchangeCatalogEntry> buyEntries,
        List<ExchangeCatalogEntry> sellEntries,
        List<StoreProduct> storeProducts
) {
    public ScenarioContext {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(seedPlan, "seedPlan");
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        browseEntries = List.copyOf(Objects.requireNonNull(browseEntries, "browseEntries"));
        buyEntries = List.copyOf(Objects.requireNonNull(buyEntries, "buyEntries"));
        sellEntries = List.copyOf(Objects.requireNonNull(sellEntries, "sellEntries"));
        storeProducts = List.copyOf(Objects.requireNonNull(storeProducts, "storeProducts"));
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Scenario context requires at least one player");
        }
        if (browseEntries.isEmpty()) {
            throw new IllegalArgumentException("Scenario context requires at least one exchange entry");
        }
    }

    public static ScenarioContext create(
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan
    ) {
        final SeedPlayerFactory playerFactory = new SeedPlayerFactory();
        final List<SeedPlayer> players = playerFactory.createPlayers(seedPlan, components.economyConfig().fractionalDigits());

        final List<ExchangeCatalogEntry> allEntries = new ArrayList<>(components.exchangeCatalog().allEntries());
        allEntries.sort(Comparator.comparing(entry -> entry.itemKey().value()));

        final List<ExchangeCatalogEntry> buyEntries = allEntries.stream()
                .filter(ExchangeCatalogEntry::buyEnabled)
                .toList();
        final List<ExchangeCatalogEntry> sellEnabledEntries = allEntries.stream()
                .filter(ExchangeCatalogEntry::sellEnabled)
                .toList();
        final List<ExchangeCatalogEntry> sellEntries = sellEnabledEntries.stream()
                .filter(entry -> components.pricingService()
                        .quoteSell(entry.itemKey(), 1, components.stockService().getSnapshot(entry.itemKey()))
                        .totalPrice()
                        .compareTo(BigDecimal.ZERO) > 0)
                .toList();
        final List<StoreProduct> storeProducts = new ArrayList<>(components.storeProductsConfig().products().values());
        storeProducts.sort(Comparator.comparing(StoreProduct::productId));

        return new ScenarioContext(
                components,
                seedPlan,
                players,
                allEntries,
                buyEntries.isEmpty() ? allEntries : buyEntries,
                sellEntries.isEmpty() ? sellEnabledEntries : sellEntries,
                storeProducts
        );
    }

    public ScenarioSelection nextBrowseSelection(final Random random) {
        return new ScenarioSelection(
                this.randomPlayer(random),
                this.randomBrowseEntry(random),
                this.randomStoreProduct(random),
                this.randomMarketActivityCategory(random),
                1 + random.nextInt(8)
        );
    }

    public ScenarioSelection nextBuySelection(final Random random) {
        return new ScenarioSelection(
                this.randomPlayer(random),
                this.randomBuyEntry(random),
                this.randomStoreProduct(random),
                this.randomMarketActivityCategory(random),
                1 + random.nextInt(8)
        );
    }

    public ScenarioSelection nextSellSelection(final Random random) {
        return new ScenarioSelection(
                this.randomPlayer(random),
                this.randomSellEntry(random),
                this.randomStoreProduct(random),
                this.randomMarketActivityCategory(random),
                1 + random.nextInt(16)
        );
    }

    public ScenarioSelection nextMixedSelection(final Random random) {
        final SeedPlayer player = this.randomPlayer(random);
        final ExchangeCatalogEntry entry = (player.playerId().getLeastSignificantBits() & 1L) == 0L
                ? this.randomBuyEntry(random)
                : this.randomSellEntry(random);
        return new ScenarioSelection(
                player,
                entry,
                this.randomStoreProduct(random),
                this.randomMarketActivityCategory(random),
                1 + random.nextInt(8)
        );
    }

    private SeedPlayer randomPlayer(final Random random) {
        return this.players.get(random.nextInt(this.players.size()));
    }

    private ExchangeCatalogEntry randomBrowseEntry(final Random random) {
        return this.browseEntries.get(random.nextInt(this.browseEntries.size()));
    }

    private ExchangeCatalogEntry randomBuyEntry(final Random random) {
        return this.buyEntries.get(random.nextInt(this.buyEntries.size()));
    }

    private ExchangeCatalogEntry randomSellEntry(final Random random) {
        return this.sellEntries.get(random.nextInt(this.sellEntries.size()));
    }

    private StoreProduct randomStoreProduct(final Random random) {
        if (this.storeProducts.isEmpty()) {
            return null;
        }
        return this.storeProducts.get(random.nextInt(this.storeProducts.size()));
    }

    private MarketActivityCategory randomMarketActivityCategory(final Random random) {
        final MarketActivityCategory[] categories = MarketActivityCategory.values();
        return categories[random.nextInt(categories.length)];
    }
}
