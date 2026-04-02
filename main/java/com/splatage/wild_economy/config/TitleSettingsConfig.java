package com.splatage.wild_economy.config;

import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record TitleSettingsConfig(Map<String, TitleOption> titles) {

    public static final TitleSettingsConfig EMPTY = new TitleSettingsConfig(Map.of());

    private static final Comparator<TitleOption> SECTION_ORDER = Comparator
            .comparingInt((TitleOption option) -> option.slot() == null ? Integer.MAX_VALUE : option.slot())
            .thenComparingInt(option -> option.tier() == null ? Integer.MAX_VALUE : option.tier())
            .thenComparing(Comparator.comparingInt(TitleOption::priority).reversed())
            .thenComparing(TitleOption::key);

    private static final Comparator<TitleOption> FAMILY_ORDER = Comparator
            .comparingInt((TitleOption option) -> option.tier() == null ? Integer.MAX_VALUE : option.tier())
            .thenComparingInt(option -> option.slot() == null ? Integer.MAX_VALUE : option.slot())
            .thenComparing(Comparator.comparingInt(TitleOption::priority).reversed())
            .thenComparing(TitleOption::key);

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

    public List<TitleOption> titlesInSection(final TitleSection section) {
        return this.titles.values().stream()
                .filter(option -> option.section() == section)
                .sorted(SECTION_ORDER)
                .toList();
    }

    public List<TitleOption> titlesInSectionFamily(final TitleSection section, final String family) {
        final String normalizedFamily = family == null ? "" : family.trim();
        return this.titles.values().stream()
                .filter(option -> option.section() == section)
                .filter(option -> normalizedFamily.equalsIgnoreCase(option.family() == null ? "" : option.family().trim()))
                .sorted(FAMILY_ORDER)
                .toList();
    }

    public List<String> familiesInSection(final TitleSection section) {
        final Set<String> families = new LinkedHashSet<>();
        for (final TitleOption option : this.titlesInSection(section)) {
            if (option.family() != null && !option.family().isBlank()) {
                families.add(option.family());
            }
        }
        return List.copyOf(families);
    }

    public TitleOption representativeForFamily(final TitleSection section, final String family) {
        return this.titlesInSectionFamily(section, family).stream().findFirst().orElse(null);
    }

    public Map<TitleSection, List<TitleOption>> titlesBySection() {
        final Map<TitleSection, List<TitleOption>> grouped = new LinkedHashMap<>();
        for (final TitleSection section : TitleSection.values()) {
            final List<TitleOption> options = this.titlesInSection(section);
            if (!options.isEmpty()) {
                grouped.put(section, options);
            }
        }
        return java.util.Collections.unmodifiableMap(grouped);
    }

    public List<TitleOption> flatten(final Collection<String> families, final TitleSection section) {
        final List<TitleOption> flattened = new ArrayList<>();
        for (final String family : families) {
            flattened.addAll(this.titlesInSectionFamily(section, family));
        }
        return List.copyOf(flattened);
    }
}
