package com.splatage.wild_economy.config;

import com.splatage.wild_economy.title.model.TitleOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TitleSettingsConfig(Map<String, TitleOption> titles) {

    public static final TitleSettingsConfig EMPTY = new TitleSettingsConfig(Map.of());

    public TitleSettingsConfig {
        titles = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(titles, "titles")));
    }

    public List<TitleOption> orderedTitles() {
        return this.titles.values().stream()
                .sorted(
                        Comparator.comparingInt(TitleOption::priority).reversed()
                                .thenComparing(option -> option.slot() == null ? Integer.MAX_VALUE : option.slot())
                                .thenComparing(TitleOption::key)
                )
                .toList();
    }
}
