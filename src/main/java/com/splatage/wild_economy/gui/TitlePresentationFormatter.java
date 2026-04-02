package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.model.ResolvedTitle;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

public final class TitlePresentationFormatter {

    private static final String TITLE = "§r§f";
    private static final String FLAVOR = "§r§7";
    private static final String LABEL = "§r§8";
    private static final String VALUE = "§r§f";
    private static final String POSITIVE = "§r§a";
    private static final String WARNING = "§r§e";
    private static final String NEGATIVE = "§r§c";
    private static final String PROMPT = "§r§e";
    private static final String RESET = "§r";

    public String menuTitle(final int page) {
        return "Titles" + (page <= 0 ? "" : " - Page " + (page + 1));
    }

    public String titleDisplayName(final TitleOption option, final boolean active) {
        final String prefix = active ? "§r§a★ " : "";
        return prefix + this.accent(option) + this.translate(option.displayName());
    }

    public List<String> titleLore(final TitleOption option, final StoreEligibilityResult eligibility, final boolean active) {
        final List<String> lore = new ArrayList<>();
        for (final String line : option.lore()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            lore.add(FLAVOR + this.translate(line));
        }
        if (!option.lore().isEmpty()) {
            lore.add(RESET);
        }
        lore.add(LABEL + "Source: " + VALUE + this.sourceLabel(option.source()));
        if (option.family() != null && !option.family().isBlank()) {
            lore.add(LABEL + "Family: " + this.familyAccent(option.family()) + this.prettyWords(option.family()));
        }
        if (option.tier() != null) {
            lore.add(LABEL + "Tier: " + VALUE + option.tier());
        }
        lore.add(LABEL + "Status: " + this.statusValue(active, eligibility));

        if (!eligibility.acquirable()) {
            lore.add(RESET);
            if (eligibility.blockedMessage() != null && !eligibility.blockedMessage().isBlank()) {
                lore.add(NEGATIVE + this.translate(eligibility.blockedMessage()));
            }
            for (final String progressLine : eligibility.progressLines()) {
                if (progressLine == null || progressLine.isBlank()) {
                    continue;
                }
                lore.add(FLAVOR + this.translate(progressLine));
            }
            if (eligibility.inspirationalMessage() != null && !eligibility.inspirationalMessage().isBlank()) {
                lore.add(WARNING + this.translate(eligibility.inspirationalMessage()));
            }
        }

        lore.add(RESET);
        lore.add(active ? POSITIVE + "Currently active"
                : eligibility.acquirable()
                ? PROMPT + "Click to use this title"
                : NEGATIVE + "Locked");
        return lore;
    }

    public String summaryDisplayName() {
        return "§r§bTitle Selection";
    }

    public List<String> summaryLore(
            final Optional<ResolvedTitle> resolvedTitle,
            final Optional<TitleOption> selectedTitle,
            final String defaultTitleText,
            final boolean usingSelectedOverride
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add(LABEL + "Current: " + (resolvedTitle.isPresent()
                ? VALUE + this.translate(resolvedTitle.get().text())
                : FLAVOR + "None"));
        lore.add(LABEL + "Default: " + (defaultTitleText == null || defaultTitleText.isBlank()
                ? FLAVOR + "None"
                : VALUE + this.translate(defaultTitleText)));
        lore.add(LABEL + "Mode: " + (usingSelectedOverride
                ? VALUE + "Using earned override"
                : VALUE + "Using default title"));
        selectedTitle.ifPresent(option -> lore.add(LABEL + "Override: " + VALUE + this.translate(option.displayName())));
        if (selectedTitle.isPresent() && !usingSelectedOverride) {
            lore.add(WARNING + "Selected override is unavailable; default title is active.");
        }
        resolvedTitle.ifPresent(title -> {
            lore.add(LABEL + "Source: " + VALUE + this.sourceLabel(title.source()));
            if (title.family() != null && !title.family().isBlank()) {
                lore.add(LABEL + "Family: " + this.familyAccent(title.family()) + this.prettyWords(title.family()));
            }
        });
        lore.add(RESET);
        lore.add(FLAVOR + "Choose an earned title to override your default.");
        return lore;
    }

    public String clearButtonName(final boolean usingDefaultTitle) {
        return usingDefaultTitle ? "§r§7Using Default Title" : "§r§eUse Default Title";
    }

    public List<String> clearButtonLore(final boolean usingDefaultTitle, final String defaultTitleText) {
        final String shownDefault = defaultTitleText == null || defaultTitleText.isBlank()
                ? FLAVOR + "No default title configured"
                : VALUE + this.translate(defaultTitleText);
        if (usingDefaultTitle) {
            return List.of(
                    LABEL + "Current default: " + shownDefault,
                    FLAVOR + "You are already using your default title."
            );
        }
        return List.of(
                LABEL + "Default title: " + shownDefault,
                FLAVOR + "Clear your earned override and return to your default title."
        );
    }

    public String previousButtonName() {
        return "§r§ePrevious Page";
    }

    public String nextButtonName() {
        return "§r§eNext Page";
    }

    public String closeButtonName() {
        return "§r§cClose";
    }

    public void applyMenuFlags(final ItemMeta meta) {
        Objects.requireNonNull(meta, "meta");
        this.addFlagIfPresent(meta, "HIDE_ATTRIBUTES");
        this.addFlagIfPresent(meta, "HIDE_ADDITIONAL_TOOLTIP");
    }

    private void addFlagIfPresent(final ItemMeta meta, final String flagName) {
        try {
            meta.addItemFlags(ItemFlag.valueOf(flagName));
        } catch (final IllegalArgumentException ignored) {
            // Server API does not expose this flag; ignore safely.
        }
    }

    private String statusValue(final boolean active, final StoreEligibilityResult eligibility) {
        if (active) {
            return POSITIVE + "Active";
        }
        if (eligibility.acquirable()) {
            return VALUE + "Available";
        }
        return NEGATIVE + "Locked";
    }

    private String sourceLabel(final TitleSource source) {
        return switch (source) {
            case DEFAULT -> "Default";
            case RELIC -> "Relic";
            case BEST_OF_ALL_TIME -> "Best of all time";
            case ACHIEVEMENT -> "Achievement";
            case AUTHORITY -> "Authority";
            case TIME_ON_SERVER -> "Time on server";
            case COMMERCE_MILESTONE -> "Commerce milestone";
            case COMMERCE_CROWN -> "Commerce crown";
            case SUPPORTER -> "Supporter";
            case EVENT -> "Event";
        };
    }

    private String accent(final TitleOption option) {
        if (option.family() != null && !option.family().isBlank()) {
            return this.familyAccent(option.family());
        }
        return switch (option.source()) {
            case DEFAULT -> "§r§7";
            case RELIC -> "§r§b";
            case BEST_OF_ALL_TIME -> "§r§6";
            case ACHIEVEMENT -> "§r§a";
            case AUTHORITY -> "§r§c";
            case TIME_ON_SERVER -> "§r§f";
            case COMMERCE_MILESTONE -> "§r§6";
            case COMMERCE_CROWN -> "§r§e";
            case SUPPORTER -> "§r§d";
            case EVENT -> "§r§a";
        };
    }

    private String familyAccent(final String family) {
        final String normalized = family == null ? "" : family.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "stormbound" -> "§r§b";
            case "earthshaper" -> "§r§2";
            case "cinderlord" -> "§r§6";
            case "tidecaller" -> "§r§3";
            case "thornwarden" -> "§r§a";
            case "voidwarden" -> "§r§5";
            case "dawnkeeper" -> "§r§e";
            case "wildheart" -> "§r§6";
            case "gearwright" -> "§r§6";
            case "bread", "provisioning", "market", "orchard", "timber", "wool", "apiary", "smithing", "industry", "masonry", "fishing", "livestock" -> "§r§6";
            case "exploration", "survival", "village", "combat" -> "§r§a";
            case "tenure", "activity" -> "§r§f";
            default -> TITLE;
        };
    }

    private String prettyWords(final String raw) {
        final StringBuilder builder = new StringBuilder();
        final String normalized = raw.replace('_', ' ').replace('-', ' ');
        for (final String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                builder.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private String translate(final String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
