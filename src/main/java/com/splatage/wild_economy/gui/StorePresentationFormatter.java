package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.EconomyFormatter;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.store.model.StoreCategory;
import com.splatage.wild_economy.store.model.StoreProduct;
import com.splatage.wild_economy.store.model.StoreProductType;
import com.splatage.wild_economy.store.state.StoreOwnershipState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

public final class StorePresentationFormatter {

    private static final String RESET = "§r";
    private static final String LABEL = "§r§8";
    private static final String VALUE = "§r§f";
    private static final String VALUE_MONEY = "§r§6";
    private static final String VALUE_POSITIVE = "§r§a";
    private static final String VALUE_WARNING = "§r§e";
    private static final String VALUE_NEGATIVE = "§r§c";
    private static final String FLAVOR = "§r§7";
    private static final String LOCKED = "§r§c";
    private static final String INSPIRATION = "§r§e";
    private static final String PROMPT = "§r§e";

    public String productDisplayName(final StoreProduct product) {
        return this.accent(product) + this.translate(product.displayName());
    }

    public String categoryDisplayName(final StoreCategory category) {
        return this.categoryAccent(category.categoryId()) + this.translate(category.displayName());
    }

    public List<String> rootCategoryLore(final StoreCategory category, final StoreEligibilityResult eligibility) {
        final List<String> lore = new ArrayList<>();
        lore.addAll(this.flavorLines(this.categoryDescription(category.categoryId()), 2));
        lore.add(RESET);
        if (!eligibility.acquirable()) {
            lore.addAll(this.eligibilitySection(eligibility));
            lore.add(RESET);
        }
        lore.add(PROMPT + "Browse category");
        return lore;
    }

    public List<String> tileLore(
            final StoreProduct product,
            final StoreEligibilityResult eligibility,
            final StoreOwnershipState ownershipState,
            final EconomyConfig economyConfig,
            final int flavorLineLimit
    ) {
        final List<String> lore = new ArrayList<>();
        lore.addAll(this.flavorLines(product.lore(), flavorLineLimit));
        if (!product.lore().isEmpty()) {
            lore.add(RESET);
        }

        this.appendMetaSection(lore, product, eligibility, ownershipState, economyConfig, false);

        if (product.type() == StoreProductType.PERMANENT_UNLOCK && ownershipState == StoreOwnershipState.OWNED) {
            lore.add(RESET);
            lore.add(VALUE_POSITIVE + "Already unlocked");
        } else if (!eligibility.acquirable()) {
            lore.add(RESET);
            lore.addAll(this.eligibilitySection(eligibility));
        }

        lore.add(RESET);
        lore.add(PROMPT + this.viewPrompt(product));
        return lore;
    }

    public List<String> detailLore(
            final StoreProduct product,
            final StoreEligibilityResult eligibility,
            final StoreOwnershipState ownershipState,
            final EconomyConfig economyConfig
    ) {
        final List<String> lore = new ArrayList<>();
        lore.addAll(this.flavorLines(product.lore(), Integer.MAX_VALUE));
        if (!product.lore().isEmpty()) {
            lore.add(RESET);
        }

        this.appendMetaSection(lore, product, eligibility, ownershipState, economyConfig, true);

        if (product.type() == StoreProductType.PERMANENT_UNLOCK && ownershipState == StoreOwnershipState.OWNED) {
            lore.add(RESET);
            lore.add(VALUE_POSITIVE + "Already unlocked");
        } else if (!eligibility.acquirable()) {
            lore.add(RESET);
            lore.addAll(this.eligibilitySection(eligibility));
        }

        lore.add(RESET);
        lore.add(LABEL + "Confirmation: " + this.confirmationValue(product.requireConfirmation()));
        return lore;
    }

    public PurchaseButtonView purchaseButton(
            final StoreProduct product,
            final StoreEligibilityResult eligibility,
            final StoreOwnershipState ownershipState,
            final EconomyConfig economyConfig
    ) {
        final List<String> lore = new ArrayList<>();
        if (product.type() == StoreProductType.XP_WITHDRAWAL) {
            lore.add(LABEL + "Action: " + VALUE + this.purchaseLabel(product.type()));
            lore.add(LABEL + "Amount: " + VALUE_WARNING + product.xpCostPoints() + " XP");
        } else {
            lore.add(LABEL + "Price: " + VALUE_MONEY + EconomyFormatter.format(product.price(), economyConfig));
            lore.add(LABEL + "Purchase: " + VALUE + this.purchaseLabel(product.type()));
        }
        lore.add(LABEL + "Status: " + this.statusValue(product.type(), ownershipState, eligibility));

        if (product.type() == StoreProductType.PERMANENT_UNLOCK && ownershipState == StoreOwnershipState.OWNED) {
            lore.add(RESET);
            lore.add(VALUE_POSITIVE + "Already unlocked");
            return new PurchaseButtonView("§r§7Owned", org.bukkit.Material.GRAY_STAINED_GLASS_PANE, lore);
        }

        if (eligibility.acquirable()) {
            lore.add(RESET);
            lore.add(PROMPT + this.purchasePrompt(product.type()));
            return new PurchaseButtonView(
                    this.availableButtonName(product.type()),
                    org.bukkit.Material.GREEN_STAINED_GLASS_PANE,
                    lore
            );
        }

        lore.add(RESET);
        lore.add(LOCKED + (eligibility.blockedMessage() == null || eligibility.blockedMessage().isBlank()
                ? "This option is locked right now."
                : this.translate(eligibility.blockedMessage())));
        return new PurchaseButtonView("§r§cLocked", org.bukkit.Material.RED_STAINED_GLASS_PANE, lore);
    }

    public String backButtonName() {
        return "§r§cBack";
    }

    public List<String> backButtonLore() {
        return List.of(FLAVOR + "Return to the product list");
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

    private void appendMetaSection(
            final List<String> lore,
            final StoreProduct product,
            final StoreEligibilityResult eligibility,
            final StoreOwnershipState ownershipState,
            final EconomyConfig economyConfig,
            final boolean includeTrackLines
    ) {
        if (includeTrackLines) {
            final RelicTrackInfo relicTrackInfo = this.relicTrackInfo(product);
            if (relicTrackInfo != null) {
                lore.add(LABEL + "Path: " + relicTrackInfo.colorCode() + relicTrackInfo.trackName());
                lore.add(LABEL + "Tier: " + VALUE + relicTrackInfo.tierLabel());
            }
        }

        if (product.type() == StoreProductType.XP_WITHDRAWAL) {
            lore.add(LABEL + "Action: " + VALUE + this.purchaseLabel(product.type()));
            lore.add(LABEL + "Amount: " + VALUE_WARNING + product.xpCostPoints() + " XP");
            lore.add(LABEL + "Status: " + this.statusValue(product.type(), ownershipState, eligibility));
            lore.add(FLAVOR + "Throw the bottle to redeem the stored XP.");
            return;
        }

        lore.add(LABEL + "Price: " + VALUE_MONEY + EconomyFormatter.format(product.price(), economyConfig));
        lore.add(LABEL + "Purchase: " + VALUE + this.purchaseLabel(product.type()));
        lore.add(LABEL + "Status: " + this.statusValue(product.type(), ownershipState, eligibility));
    }

    private List<String> eligibilitySection(final StoreEligibilityResult eligibility) {
        final List<String> lines = new ArrayList<>();
        if (eligibility.blockedMessage() != null && !eligibility.blockedMessage().isBlank()) {
            lines.add(LOCKED + this.translate(eligibility.blockedMessage()));
        }
        for (final String progressLine : eligibility.progressLines()) {
            if (progressLine == null || progressLine.isBlank()) {
                continue;
            }
            lines.add(FLAVOR + this.translate(progressLine));
        }
        if (eligibility.inspirationalMessage() != null && !eligibility.inspirationalMessage().isBlank()) {
            lines.add(INSPIRATION + this.translate(eligibility.inspirationalMessage()));
        }
        return lines;
    }

    private List<String> flavorLines(final List<String> lines, final int limit) {
        final List<String> styled = new ArrayList<>();
        int count = 0;
        for (final String line : lines) {
            if (count >= limit) {
                break;
            }
            if (line == null || line.isBlank()) {
                continue;
            }
            styled.add(FLAVOR + this.translate(line));
            count++;
        }
        return styled;
    }

    private List<String> flavorLines(final String description, final int limit) {
        final String[] parts = description.split("\\n");
        final List<String> lines = new ArrayList<>();
        for (final String part : parts) {
            lines.add(part);
        }
        return this.flavorLines(lines, limit);
    }

    private String purchaseLabel(final StoreProductType type) {
        return switch (type) {
            case PERMANENT_UNLOCK -> "One-time unlock";
            case REPEATABLE_GRANT -> "Repeatable purchase";
            case XP_WITHDRAWAL -> "Withdraw XP";
        };
    }

    private String purchasePrompt(final StoreProductType type) {
        return switch (type) {
            case PERMANENT_UNLOCK -> "Click to unlock now";
            case REPEATABLE_GRANT -> "Click to buy now";
            case XP_WITHDRAWAL -> "Click to withdraw this bottle";
        };
    }

    private String availableButtonName(final StoreProductType type) {
        return switch (type) {
            case PERMANENT_UNLOCK -> "§r§aUnlock Now";
            case REPEATABLE_GRANT -> "§r§aBuy Now";
            case XP_WITHDRAWAL -> "§r§aWithdraw XP";
        };
    }

    private String confirmationValue(final boolean requireConfirmation) {
        return requireConfirmation ? VALUE_WARNING + "Required" : VALUE_POSITIVE + "Not required";
    }

    private String viewPrompt(final StoreProduct product) {
        return switch (product.type()) {
            case PERMANENT_UNLOCK -> "View unlock details";
            case REPEATABLE_GRANT -> "View purchase details";
            case XP_WITHDRAWAL -> "View XP withdrawal details";
        };
    }

    private String statusValue(
            final StoreProductType type,
            final StoreOwnershipState ownershipState,
            final StoreEligibilityResult eligibility
    ) {
        return switch (type) {
            case PERMANENT_UNLOCK -> switch (ownershipState) {
                case OWNED -> VALUE_POSITIVE + "Owned";
                case LOADING -> VALUE_WARNING + "Checking ownership";
                case LOAD_FAILED -> VALUE_NEGATIVE + "Ownership unavailable";
                case NOT_OWNED -> eligibility.acquirable() ? VALUE_POSITIVE + "Available" : VALUE_NEGATIVE + "Locked";
            };
            case REPEATABLE_GRANT -> eligibility.acquirable() ? VALUE_POSITIVE + "Ready to buy" : VALUE_NEGATIVE + "Locked";
            case XP_WITHDRAWAL -> eligibility.acquirable() ? VALUE_POSITIVE + "Available" : VALUE_NEGATIVE + "Unavailable";
        };
    }

    private String accent(final StoreProduct product) {
        if (!"relic_hall".equals(product.categoryId())) {
            return "§r§6";
        }
        final String entitlementKey = product.entitlementKey();
        if (entitlementKey == null || entitlementKey.isBlank()) {
            return "§r§d";
        }
        if (entitlementKey.startsWith("prestige.stormbound.")) {
            return "§r§b";
        }
        if (entitlementKey.startsWith("prestige.earthshaper.")) {
            return "§r§2";
        }
        if (entitlementKey.startsWith("prestige.cinderlord.")) {
            return "§r§6";
        }
        if (entitlementKey.startsWith("prestige.tidecaller.")) {
            return "§r§3";
        }
        if (entitlementKey.startsWith("prestige.thornwarden.")) {
            return "§r§a";
        }
        if (entitlementKey.startsWith("prestige.voidwarden.")) {
            return "§r§5";
        }
        if (entitlementKey.startsWith("prestige.dawnkeeper.")) {
            return "§r§e";
        }
        if (entitlementKey.startsWith("prestige.wildheart.")) {
            return "§r§6";
        }
        if (entitlementKey.startsWith("prestige.gearwright.")) {
            return "§r§6";
        }
        return "§r§d";
    }

    private String categoryAccent(final String categoryId) {
        return switch (categoryId) {
            case "kits" -> "§r§a";
            case "relic_hall" -> "§r§d";
            case "spawners" -> "§r§6";
            case "ranks" -> "§r§e";
            case "perks" -> "§r§b";
            case "tools" -> "§r§6";
            case "xp_bottles" -> "§r§5";
            default -> "§r§f";
        };
    }

    private String categoryDescription(final String categoryId) {
        return switch (categoryId) {
            case "kits" -> "Practical bundles for getting started\nand staying supplied.";
            case "relic_hall" -> "Nine gated prestige paths with\nrelic gear and capstones.";
            case "spawners" -> "Spawner unlocks for farm,\nprogression, and utility setups.";
            case "ranks" -> "Permanent supporter and\nserver standing unlocks.";
            case "perks" -> "Convenience and claim-based\nquality-of-life upgrades.";
            case "tools" -> "WildTools utility items for\nfaster building and harvesting.";
            case "xp_bottles" -> "Bottle up your experience\nfor later use.";
            default -> "Browse this category.";
        };
    }

    private String translate(final String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private RelicTrackInfo relicTrackInfo(final StoreProduct product) {
        if (!"relic_hall".equals(product.categoryId())) {
            return null;
        }
        final String entitlementKey = product.entitlementKey();
        if (entitlementKey == null || entitlementKey.isBlank()) {
            return null;
        }
        final String[] parts = entitlementKey.split("\\.");
        if (parts.length < 3 || !"prestige".equals(parts[0])) {
            return null;
        }
        final String track = parts[1];
        final String tier = parts[2];
        return new RelicTrackInfo(this.prettyWords(track), this.tierLabel(tier), this.accent(product));
    }

    private String tierLabel(final String tier) {
        return switch (tier) {
            case "1" -> "I";
            case "2" -> "II";
            case "3" -> "III";
            case "4" -> "IV";
            case "5", "capstone" -> "V / Capstone";
            default -> this.prettyWords(tier);
        };
    }

    private String prettyWords(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        final String[] parts = raw.replace(':', ' ')
                .replace('/', ' ')
                .replace('.', ' ')
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .split("\\s+");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    public record PurchaseButtonView(String name, org.bukkit.Material material, List<String> lore) {
        public PurchaseButtonView {
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }
    }

    private record RelicTrackInfo(String trackName, String tierLabel, String colorCode) {
    }
}
