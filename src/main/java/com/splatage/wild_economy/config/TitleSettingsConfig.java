package com.splatage.wild_economy.config;

import com.splatage.wild_economy.title.model.TitleOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TitleSettingsConfig(
    Map<String, TitleOption> options
) {
    public static final TitleSettingsConfig EMPTY = new TitleSettingsConfig(Map.of());

    public TitleSettingsConfig {
        options = Map.copyOf(Objects.requireNonNull(options, "options"));
    }

    public Map<String, TitleOption> optionsByPath() {
        final Map<String, TitleOption> ordered = new LinkedHashMap<>();
        options.values().stream()
            .sorted(TitleOption.DISPLAY_ORDER)
            .forEach(option -> ordered.put(option.key(), option));
        return ordered;
    }
}
