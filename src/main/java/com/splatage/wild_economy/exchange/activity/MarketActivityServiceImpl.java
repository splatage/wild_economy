package com.splatage.wild_economy.exchange.activity;

import com.splatage.wild_economy.exchange.catalog.ExchangeCatalog;
import com.splatage.wild_economy.exchange.catalog.ExchangeCatalogEntry;
import com.splatage.wild_economy.exchange.domain.ItemKey;
import com.splatage.wild_economy.exchange.repository.ExchangeTransactionRepository;
import com.splatage.wild_economy.exchange.repository.SupplierStatsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketActivityServiceImpl implements MarketActivityService {

    private final ExchangeTransactionRepository exchangeTransactionRepository;
    private final SupplierStatsRepository supplierStatsRepository;
    private final ExchangeCatalog exchangeCatalog;
    private final int recentWindowHours;

    public MarketActivityServiceImpl(
        final ExchangeTransactionRepository exchangeTransactionRepository,
        final SupplierStatsRepository supplierStatsRepository,
        final ExchangeCatalog exchangeCatalog,
        final int recentWindowHours
    ) {
        this.exchangeTransactionRepository = Objects.requireNonNull(exchangeTransactionRepository, "exchangeTransactionRepository");
        this.supplierStatsRepository = Objects.requireNonNull(supplierStatsRepository, "supplierStatsRepository");
        this.exchangeCatalog = Objects.requireNonNull(exchangeCatalog, "exchangeCatalog");
        this.recentWindowHours = Math.max(1, recentWindowHours);
    }

    @Override
    public List<MarketActivityItemView> listItems(final MarketActivityCategory category, final UUID playerId, final int limit) {
        final int safeLimit = Math.max(1, limit);
        final long sinceEpochSecond = Instant.now().minusSeconds((long) this.recentWindowHours * 3600L).getEpochSecond();

        final List<MarketActivityRecord> records = switch (Objects.requireNonNull(category, "category")) {
            case RECENTLY_STOCKED -> this.supplierStatsRepository.loadRecentlyStocked(sinceEpochSecond, safeLimit);
            case RECENTLY_PURCHASED -> this.exchangeTransactionRepository.loadRecentlyPurchased(sinceEpochSecond, safeLimit);
            case TOP_TURNOVER -> this.exchangeTransactionRepository.loadTopTurnover(sinceEpochSecond, safeLimit);
            case YOUR_RECENT_PURCHASES -> this.exchangeTransactionRepository.loadPlayerRecentPurchases(
                Objects.requireNonNull(playerId, "playerId"),
                sinceEpochSecond,
                safeLimit
            );
        };

        final List<MarketActivityItemView> views = new ArrayList<>();
        for (final MarketActivityRecord record : records) {
            final ItemKey itemKey = new ItemKey(record.itemKey());
            final Optional<ExchangeCatalogEntry> entry = this.exchangeCatalog.get(itemKey);
            if (entry.isEmpty()) {
                continue;
            }
            final ExchangeCatalogEntry catalogEntry = entry.get();
            views.add(new MarketActivityItemView(
                itemKey,
                this.displayName(catalogEntry),
                record.eventEpochSecond(),
                record.totalValue() == null ? BigDecimal.ZERO : record.totalValue(),
                record.amount()
            ));
            if (views.size() >= safeLimit) {
                break;
            }
        }
        return List.copyOf(views);
    }

    private String displayName(final ExchangeCatalogEntry entry) {
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            return entry.displayName();
        }
        final String path = entry.itemKey().value().replaceFirst("^[^:]+:", "");
        final String[] segments = path.split("_");
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            final String segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }
            builder.append(segment.substring(0, 1).toUpperCase(Locale.ROOT));
            if (segment.length() > 1) {
                builder.append(segment.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }
}
