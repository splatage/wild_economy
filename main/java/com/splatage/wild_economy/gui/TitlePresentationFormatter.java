package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.model.ResolvedTitle;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSection;
import com.splatage.wild_economy.title.model.TitleSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

    public String menuTitle(final TitleMenuHolder.View view, final TitleSection section, final String family, final int page) {
        return switch (view) {
            case HUB -> "Titles";
            case SECTION -> section.displayName() + (page <= 0 ? "" : " - Page " + (page + 1));
            case FAMILY -> this.familyAccent(family) + this.prettyWords(family) + RESET + " §8- §f" + section.displayName()
                    + (page <= 0 ? "" : " - Page " + (page + 1));
        };
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
        lore.add(LABEL + "Section: " + this.sectionAccent(option.section()) + option.section().displayName());
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
                ? PROMPT + "Click to make this your active title"
                : NEGATIVE + "Locked");
        return lore;
    }

    public String summaryDisplayName() {
        return "§r§bTitle Settings";
    }

    public List<String> summaryLore(final Optional<ResolvedTitle> resolvedTitle, final Optional<TitleOption> selectedTitle) {
        final List<String> lore = new ArrayList<>();
        lore.add(LABEL + "Current: " + (resolvedTitle.isPresent()
                ? VALUE + this.translate(resolvedTitle.get().text())
                : FLAVOR + "None"));
        lore.add(LABEL + "Selection: " + (selectedTitle.isPresent()
                ? VALUE + this.translate(selectedTitle.get().displayName())
                : FLAVOR + "Automatic best available"));
        resolvedTitle.ifPresent(title -> {
            lore.add(LABEL + "Source: " + VALUE + this.sourceLabel(title.source()));
            if (title.family() != null && !title.family().isBlank()) {
                lore.add(LABEL + "Family: " + this.familyAccent(title.family()) + this.prettyWords(title.family()));
            }
        });
        lore.add(RESET);
        lore.add(FLAVOR + "Choose which earned title you want to display.");
        return lore;
    }

    public String clearButtonName(final boolean automaticMode) {
        return automaticMode ? "§r§7Automatic selection" : "§r§cClear active title";
    }

    public List<String> clearButtonLore(final boolean automaticMode) {
        if (automaticMode) {
            return List.of(
                    FLAVOR + "No manual title is selected.",
                    FLAVOR + "The highest-priority eligible title is used."
            );
        }
        return List.of(
                FLAVOR + "Remove your manual selection.",
                FLAVOR + "The highest-priority eligible title will be used."
        );
    }

    public String previousButtonName() {
        return "§r§ePrevious Page";
    }

    public String nextButtonName() {
        return "§r§eNext Page";
    }

    public String backButtonName() {
        return "§r§eBack";
    }

    public String closeButtonName() {
        return "§r§cReturn to Store";
    }

    public String hubButtonName(final TitleSection section) {
        return this.sectionAccent(section) + section.displayName();
    }

    public List<String> hubButtonLore(final TitleSection section, final int availableCount, final int totalCount) {
        final List<String> lore = new ArrayList<>();
        lore.add(FLAVOR + switch (section) {
            case RELIC -> "Choose a relic path, then select the step or capstone you want to display.";
            case BEST_OF_ALL_TIME -> "Titles for market legends and all-time honours.";
            case ACHIEVEMENT -> "Titles for major world accomplishments and milestones.";
            case AUTHORITY -> "Member, staff, VIP, admin, and owner titles.";
            case TIME_ON_SERVER -> "Titles earned through time spent and long-term presence.";
        });
        lore.add(RESET);
        lore.add(LABEL + "Available: " + VALUE + availableCount + " / " + totalCount);
        lore.add(PROMPT + "Click to browse");
        return lore;
    }

    public String familyButtonName(final TitleSection section, final String family) {
        return this.familyAccent(family) + this.prettyFamilyDisplay(section, family);
    }

    public List<String> familyButtonLore(
            final TitleSection section,
            final String family,
            final int availableCount,
            final int totalCount,
            final TitleOption representative
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add(FLAVOR + switch (section) {
            case RELIC -> "Choose which step of this relic path you want to display.";
            case BEST_OF_ALL_TIME -> "Titles awarded for lasting market prestige in this domain.";
            case ACHIEVEMENT -> "Browse achievement titles from this track.";
            case AUTHORITY -> "Browse authority titles.";
            case TIME_ON_SERVER -> "Browse long-term presence titles.";
        });
        if (representative != null && representative.tier() != null) {
            lore.add(FLAVOR + "Includes tiers " + VALUE + "1" + FLAVOR + " to " + VALUE + totalCount + FLAVOR + ".");
        }
        lore.add(RESET);
        lore.add(LABEL + "Available: " + VALUE + availableCount + " / " + totalCount);
        lore.add(PROMPT + "Click to open");
        return lore;
    }

    public Material sectionIcon(final TitleSection section) {
        return switch (section) {
            case RELIC -> Material.NETHER_STAR;
            case BEST_OF_ALL_TIME -> Material.GOLD_INGOT;
            case ACHIEVEMENT -> Material.DRAGON_HEAD;
            case AUTHORITY -> Material.NAME_TAG;
            case TIME_ON_SERVER -> Material.CLOCK;
        };
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
            // ignore safely when flag absent on this API version
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
            case RELIC -> "Relic";
            case COMMERCE_MILESTONE -> "Commerce milestone";
            case COMMERCE_CROWN -> "Commerce crown";
            case ACHIEVEMENT -> "Achievement";
            case AUTHORITY -> "Authority";
            case TIME_ON_SERVER -> "Time on server";
            case SUPPORTER -> "Supporter";
            case EVENT -> "Event";
        };
    }

    private String accent(final TitleOption option) {
        if (option.family() != null && !option.family().isBlank()) {
            return this.familyAccent(option.family());
        }
        return this.sectionAccent(option.section());
    }

    private String sectionAccent(final TitleSection section) {
        return switch (section) {
            case RELIC -> "§r§b";
            case BEST_OF_ALL_TIME -> "§r§6";
            case ACHIEVEMENT -> "§r§a";
            case AUTHORITY -> "§r§d";
            case TIME_ON_SERVER -> "§r§e";
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
            case "bread" -> "§r§6";
            case "market" -> "§r§6";
            case "achievement" -> "§r§a";
            case "authority" -> "§r§d";
            case "activity" -> "§r§e";
            default -> TITLE;
        };
    }

    private String prettyFamilyDisplay(final TitleSection section, final String family) {
        return switch (section) {
            case RELIC -> this.prettyWords(family);
            case BEST_OF_ALL_TIME -> {
                if ("market".equalsIgnoreCase(family)) {
                    yield "Market Honours";
                }
                yield this.prettyWords(family);
            }
            case TIME_ON_SERVER -> {
                if ("activity".equalsIgnoreCase(family)) {
                    yield "Activity Ladder";
                }
                yield this.prettyWords(family);
            }
            default -> this.prettyWords(family);
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
