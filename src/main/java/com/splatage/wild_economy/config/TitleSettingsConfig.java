package com.splatage.wild_economy.config;

import com.splatage.wild_economy.title.model.TitleOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TitleSettingsConfig(Map<String, TitleOption> titles) {

    public static final TitleSettingsConfig EMPTY = new TitleSettingsConfig(Map.of());

    public TitleSettingsConfig {
        titles = Map.copyOf(Objects.requireNonNull(titles, "titles"));
    }

    public List<TitleOption> orderedTitles() {
        return this.titles.values().stream()
                .sorted(Comparator.comparingInt(TitleOption::priority).reversed()
                        .thenComparing(TitleOption::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
