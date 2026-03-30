package com.splatage.wild_economy.exchange.item;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

public final class ExchangeItemCodec {

    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final Set<Material> SUPPORTED_POTION_MATERIALS = Set.of(
        Material.POTION,
        Material.SPLASH_POTION,
        Material.LINGERING_POTION
    );

    public Optional<ItemKey> itemKeyForExchange(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() <= 0) {
            return Optional.empty();
        }

        return switch (itemStack.getType()) {
            case ENCHANTED_BOOK -> this.encodeEnchantedBook(itemStack);
            case FIREWORK_ROCKET -> this.encodeFireworkRocket(itemStack);
            default -> {
                if (SUPPORTED_POTION_MATERIALS.contains(itemStack.getType())) {
                    yield this.encodePotion(itemStack);
                }
                if (this.hasUnsupportedSharedMeta(itemStack.getItemMeta())) {
                    yield Optional.empty();
                }
                yield Optional.of(this.baseItemKey(itemStack.getType()));
            }
        };
    }

    public Optional<ItemKey> baseCatalogKey(final ItemKey itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");
        final ParsedItemKey parsed = this.parse(itemKey.value());
        if (parsed == null || parsed.qualifiers().isEmpty()) {
            return Optional.empty();
        }
        if (!this.supportsDerivedVariant(parsed)) {
            return Optional.empty();
        }
        return Optional.of(new ItemKey(MINECRAFT_NAMESPACE + ":" + parsed.basePath()));
    }

    public Optional<String> displayName(final ItemKey itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");
        final ParsedItemKey parsed = this.parse(itemKey.value());
        if (parsed == null || parsed.qualifiers().isEmpty()) {
            return Optional.empty();
        }

        return switch (parsed.basePath()) {
            case "potion", "splash_potion", "lingering_potion" -> this.potionDisplayName(parsed);
            case "enchanted_book" -> this.enchantedBookDisplayName(parsed);
            case "firework_rocket" -> this.fireworkDisplayName(parsed);
            default -> Optional.empty();
        };
    }


    public Optional<PotionDescriptor> potionDescriptor(final ItemKey itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");
        final ParsedItemKey parsed = this.parse(itemKey.value());
        if (parsed == null || parsed.qualifiers().size() < 2) {
            return Optional.empty();
        }
        return switch (parsed.basePath()) {
            case "potion", "splash_potion", "lingering_potion" -> Optional.of(new PotionDescriptor(
                parsed.basePath(),
                parsed.qualifiers().get(0),
                parsed.qualifiers().get(1)
            ));
            default -> Optional.empty();
        };
    }

    public Optional<FireworkDescriptor> fireworkDescriptor(final ItemKey itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");
        final ParsedItemKey parsed = this.parse(itemKey.value());
        if (parsed == null || parsed.qualifiers().size() != 1 || !"firework_rocket".equals(parsed.basePath())) {
            return Optional.empty();
        }
        final String qualifier = parsed.qualifiers().getFirst();
        if (!qualifier.startsWith("flight_")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new FireworkDescriptor(Integer.parseInt(qualifier.substring("flight_".length()))));
        } catch (final NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Optional<ItemStack> createItemStack(final ItemKey itemKey, final int amount) {
        Objects.requireNonNull(itemKey, "itemKey");
        final ParsedItemKey parsed = this.parse(itemKey.value());
        if (parsed == null) {
            return Optional.empty();
        }

        if (parsed.qualifiers().isEmpty()) {
            final Material material = this.materialFromBasePath(parsed.basePath());
            if (material == null) {
                return Optional.empty();
            }
            return Optional.of(new ItemStack(material, Math.max(1, amount)));
        }

        return switch (parsed.basePath()) {
            case "potion", "splash_potion", "lingering_potion" -> this.decodePotion(parsed, amount);
            case "enchanted_book" -> this.decodeEnchantedBook(parsed, amount);
            case "firework_rocket" -> this.decodeFireworkRocket(parsed, amount);
            default -> Optional.empty();
        };
    }

    public String metadataJson(final ItemKey itemKey) {
        Objects.requireNonNull(itemKey, "itemKey");
        final ParsedItemKey parsed = this.parse(itemKey.value());
        if (parsed == null || parsed.qualifiers().isEmpty()) {
            return null;
        }

        return switch (parsed.basePath()) {
            case "potion", "splash_potion", "lingering_potion" -> this.potionMetadataJson(parsed).orElse(null);
            case "enchanted_book" -> this.enchantedBookMetadataJson(parsed).orElse(null);
            case "firework_rocket" -> this.fireworkMetadataJson(parsed).orElse(null);
            default -> null;
        };
    }

    private Optional<ItemKey> encodePotion(final ItemStack itemStack) {
        if (!(itemStack.getItemMeta() instanceof PotionMeta potionMeta)) {
            return Optional.empty();
        }
        if (this.hasUnsupportedSharedMeta(potionMeta) || potionMeta.hasCustomEffects() || potionMeta.hasColor()) {
            return Optional.empty();
        }

        final PotionData potionData = potionMeta.getBasePotionData();
        final PotionType potionType = potionData.getType();
        if (potionType == null) {
            return Optional.empty();
        }

        final String mode = potionData.isUpgraded() ? "strong" : potionData.isExtended() ? "long" : "normal";
        return Optional.of(new ItemKey(
            MINECRAFT_NAMESPACE + ":"
                + itemStack.getType().name().toLowerCase(Locale.ROOT)
                + "/"
                + potionType.name().toLowerCase(Locale.ROOT)
                + "/"
                + mode
        ));
    }

    private Optional<ItemKey> encodeEnchantedBook(final ItemStack itemStack) {
        if (!(itemStack.getItemMeta() instanceof EnchantmentStorageMeta enchantmentStorageMeta)) {
            return Optional.empty();
        }
        if (this.hasUnsupportedSharedMeta(enchantmentStorageMeta)) {
            return Optional.empty();
        }

        final Map<String, Integer> storedEnchants = new TreeMap<>();
        for (final Map.Entry<Enchantment, Integer> entry : enchantmentStorageMeta.getStoredEnchants().entrySet()) {
            final Enchantment enchantment = entry.getKey();
            if (enchantment == null || enchantment.getKey() == null || !MINECRAFT_NAMESPACE.equals(enchantment.getKey().getNamespace())) {
                return Optional.empty();
            }
            storedEnchants.put(enchantment.getKey().getKey(), entry.getValue());
        }

        if (storedEnchants.isEmpty()) {
            return Optional.empty();
        }

        final String encoded = storedEnchants.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("+"));

        return Optional.of(new ItemKey(MINECRAFT_NAMESPACE + ":enchanted_book/" + encoded));
    }

    private Optional<ItemKey> encodeFireworkRocket(final ItemStack itemStack) {
        if (!(itemStack.getItemMeta() instanceof FireworkMeta fireworkMeta)) {
            return Optional.empty();
        }
        if (this.hasUnsupportedSharedMeta(fireworkMeta)) {
            return Optional.empty();
        }
        if (!fireworkMeta.getEffects().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ItemKey(MINECRAFT_NAMESPACE + ":firework_rocket/flight_" + fireworkMeta.getPower()));
    }

    private Optional<ItemStack> decodePotion(final ParsedItemKey parsed, final int amount) {
        if (parsed.qualifiers().size() < 2) {
            return Optional.empty();
        }

        final Material material = this.materialFromBasePath(parsed.basePath());
        final PotionType potionType = this.parsePotionType(parsed.qualifiers().get(0));
        final PotionMode potionMode = PotionMode.fromToken(parsed.qualifiers().get(1));
        if (material == null || potionType == null || potionMode == null) {
            return Optional.empty();
        }

        final ItemStack stack = new ItemStack(material, Math.max(1, amount));
        if (!(stack.getItemMeta() instanceof PotionMeta potionMeta)) {
            return Optional.empty();
        }

        try {
            potionMeta.setBasePotionData(new PotionData(potionType, potionMode.extended(), potionMode.upgraded()));
        } catch (final IllegalArgumentException exception) {
            return Optional.empty();
        }

        stack.setItemMeta(potionMeta);
        return Optional.of(stack);
    }

    private Optional<ItemStack> decodeEnchantedBook(final ParsedItemKey parsed, final int amount) {
        if (parsed.qualifiers().size() != 1) {
            return Optional.empty();
        }

        final ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK, Math.max(1, amount));
        if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta enchantmentStorageMeta)) {
            return Optional.empty();
        }

        final String encoded = parsed.qualifiers().getFirst();
        if (encoded.isBlank()) {
            return Optional.empty();
        }

        for (final String token : encoded.split("\\+")) {
            final int separator = token.indexOf('=');
            if (separator <= 0 || separator >= token.length() - 1) {
                return Optional.empty();
            }
            final String enchantmentKey = token.substring(0, separator);
            final int level;
            try {
                level = Integer.parseInt(token.substring(separator + 1));
            } catch (final NumberFormatException exception) {
                return Optional.empty();
            }
            if (level <= 0) {
                return Optional.empty();
            }

            final Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentKey));
            if (enchantment == null) {
                return Optional.empty();
            }
            enchantmentStorageMeta.addStoredEnchant(enchantment, level, true);
        }

        stack.setItemMeta(enchantmentStorageMeta);
        return Optional.of(stack);
    }

    private Optional<ItemStack> decodeFireworkRocket(final ParsedItemKey parsed, final int amount) {
        if (parsed.qualifiers().size() != 1) {
            return Optional.empty();
        }
        final String qualifier = parsed.qualifiers().getFirst();
        if (!qualifier.startsWith("flight_")) {
            return Optional.empty();
        }

        final int power;
        try {
            power = Integer.parseInt(qualifier.substring("flight_".length()));
        } catch (final NumberFormatException exception) {
            return Optional.empty();
        }
        if (power < 0) {
            return Optional.empty();
        }

        final ItemStack stack = new ItemStack(Material.FIREWORK_ROCKET, Math.max(1, amount));
        if (!(stack.getItemMeta() instanceof FireworkMeta fireworkMeta)) {
            return Optional.empty();
        }
        fireworkMeta.setPower(power);
        stack.setItemMeta(fireworkMeta);
        return Optional.of(stack);
    }

    private Optional<String> potionDisplayName(final ParsedItemKey parsed) {
        if (parsed.qualifiers().size() < 2) {
            return Optional.empty();
        }
        final PotionType potionType = this.parsePotionType(parsed.qualifiers().get(0));
        final PotionMode potionMode = PotionMode.fromToken(parsed.qualifiers().get(1));
        if (potionType == null || potionMode == null) {
            return Optional.empty();
        }
        final String materialPrefix = switch (parsed.basePath()) {
            case "splash_potion" -> "Splash Potion";
            case "lingering_potion" -> "Lingering Potion";
            default -> "Potion";
        };
        final StringBuilder builder = new StringBuilder(materialPrefix).append(" of ").append(this.potionTypeName(potionType));
        if (potionMode.upgraded()) {
            builder.append(" II");
        }
        if (potionMode.extended()) {
            builder.append(" (Extended)");
        }
        return Optional.of(builder.toString());
    }

    private Optional<String> enchantedBookDisplayName(final ParsedItemKey parsed) {
        if (parsed.qualifiers().size() != 1) {
            return Optional.empty();
        }
        final List<String> parts = new ArrayList<>();
        for (final String token : parsed.qualifiers().getFirst().split("\\+")) {
            final int separator = token.indexOf('=');
            if (separator <= 0 || separator >= token.length() - 1) {
                return Optional.empty();
            }
            final String enchantmentName = this.enchantmentDisplayName(token.substring(0, separator));
            final int level;
            try {
                level = Integer.parseInt(token.substring(separator + 1));
            } catch (final NumberFormatException exception) {
                return Optional.empty();
            }
            if (level <= 0) {
                return Optional.empty();
            }
            parts.add(enchantmentName + " " + this.romanNumeral(level));
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Enchanted Book (" + String.join(", ", parts) + ")");
    }

    private Optional<String> fireworkDisplayName(final ParsedItemKey parsed) {
        if (parsed.qualifiers().size() != 1) {
            return Optional.empty();
        }
        final String qualifier = parsed.qualifiers().getFirst();
        if (!qualifier.startsWith("flight_")) {
            return Optional.empty();
        }
        final String power = qualifier.substring("flight_".length());
        if (power.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("Firework Rocket (Flight " + power + ")");
    }

    private Optional<String> potionMetadataJson(final ParsedItemKey parsed) {
        if (parsed.qualifiers().size() < 2) {
            return Optional.empty();
        }
        final PotionType potionType = this.parsePotionType(parsed.qualifiers().get(0));
        final PotionMode potionMode = PotionMode.fromToken(parsed.qualifiers().get(1));
        if (potionType == null || potionMode == null) {
            return Optional.empty();
        }
        return Optional.of(
            "{\"kind\":\"potion\",\"material\":\"" + escapeJson(parsed.basePath()) + "\",\"type\":\""
                + escapeJson(potionType.name().toLowerCase(Locale.ROOT))
                + "\",\"mode\":\""
                + escapeJson(potionMode.token())
                + "\"}"
        );
    }

    private Optional<String> enchantedBookMetadataJson(final ParsedItemKey parsed) {
        if (parsed.qualifiers().size() != 1) {
            return Optional.empty();
        }
        final List<String> enchantments = new ArrayList<>();
        for (final String token : parsed.qualifiers().getFirst().split("\\+")) {
            final int separator = token.indexOf('=');
            if (separator <= 0 || separator >= token.length() - 1) {
                return Optional.empty();
            }
            final String key = token.substring(0, separator);
            final String level = token.substring(separator + 1);
            enchantments.add("{\"key\":\"" + escapeJson(key) + "\",\"level\":" + escapeJson(level) + "}");
        }
        return Optional.of("{\"kind\":\"enchanted_book\",\"enchants\":[" + String.join(",", enchantments) + "]}");
    }

    private Optional<String> fireworkMetadataJson(final ParsedItemKey parsed) {
        if (parsed.qualifiers().size() != 1) {
            return Optional.empty();
        }
        final String qualifier = parsed.qualifiers().getFirst();
        if (!qualifier.startsWith("flight_")) {
            return Optional.empty();
        }
        final String power = qualifier.substring("flight_".length());
        if (power.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("{\"kind\":\"firework_rocket\",\"flight\":" + escapeJson(power) + "}");
    }

    private boolean supportsDerivedVariant(final ParsedItemKey parsed) {
        return switch (parsed.basePath()) {
            case "potion", "splash_potion", "lingering_potion" -> parsed.qualifiers().size() >= 2;
            case "enchanted_book", "firework_rocket" -> parsed.qualifiers().size() == 1;
            default -> false;
        };
    }

    private ParsedItemKey parse(final String rawItemKey) {
        if (rawItemKey == null || rawItemKey.isBlank()) {
            return null;
        }
        final int namespaceSeparator = rawItemKey.indexOf(':');
        if (namespaceSeparator <= 0 || namespaceSeparator >= rawItemKey.length() - 1) {
            return null;
        }
        final String namespace = rawItemKey.substring(0, namespaceSeparator);
        if (!MINECRAFT_NAMESPACE.equals(namespace)) {
            return null;
        }
        final String path = rawItemKey.substring(namespaceSeparator + 1).toLowerCase(Locale.ROOT);
        final String[] parts = path.split("/");
        final String basePath = parts[0];
        final List<String> qualifiers = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                qualifiers.add(parts[i]);
            }
        }
        return new ParsedItemKey(namespace, basePath, List.copyOf(qualifiers));
    }

    private Material materialFromBasePath(final String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return null;
        }
        return Material.matchMaterial(basePath.toUpperCase(Locale.ROOT));
    }

    private ItemKey baseItemKey(final Material material) {
        return new ItemKey(MINECRAFT_NAMESPACE + ":" + material.name().toLowerCase(Locale.ROOT));
    }

    private PotionType parsePotionType(final String token) {
        try {
            return PotionType.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasUnsupportedSharedMeta(final ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName()) {
            return true;
        }
        if (meta.hasLore()) {
            return true;
        }
        if (meta.hasEnchants()) {
            return true;
        }
        if (meta.isUnbreakable()) {
            return true;
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            return true;
        }
        if (meta instanceof BlockStateMeta blockStateMeta
            && blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox
            && this.hasAnyContents(shulkerBox)) {
            return true;
        }
        return false;
    }

    private boolean hasAnyContents(final ShulkerBox shulkerBox) {
        for (final ItemStack contents : shulkerBox.getInventory().getContents()) {
            if (contents == null || contents.getType() == Material.AIR || contents.getAmount() <= 0) {
                continue;
            }
            return true;
        }
        return false;
    }

    private String potionTypeName(final PotionType potionType) {
        final String name = potionType.name();
        return switch (name) {
            case "INSTANT_DAMAGE", "HARMING" -> "Harming";
            case "INSTANT_HEAL", "HEALING" -> "Healing";
            case "JUMP", "LEAPING" -> "Leaping";
            case "SPEED", "SWIFTNESS" -> "Swiftness";
            default -> this.titleCase(name);
        };
    }

    private String enchantmentDisplayName(final String key) {
        return this.titleCase(key);
    }

    private String titleCase(final String raw) {
        final String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\-]");
        return java.util.Arrays.stream(parts)
            .filter(part -> !part.isBlank())
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(Collectors.joining(" "));
    }

    private String romanNumeral(final int value) {
        if (value <= 0) {
            return Integer.toString(value);
        }
        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        int remaining = value;
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                builder.append(numerals[i]);
                remaining -= values[i];
            }
        }
        return builder.toString();
    }

    private static String escapeJson(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record PotionDescriptor(String basePath, String potionTypeToken, String modeToken) {
    }

    public record FireworkDescriptor(int flightPower) {
    }

    private record ParsedItemKey(String namespace, String basePath, List<String> qualifiers) {
    }

    private enum PotionMode {
        NORMAL("normal", false, false),
        LONG("long", true, false),
        STRONG("strong", false, true);

        private final String token;
        private final boolean extended;
        private final boolean upgraded;

        PotionMode(final String token, final boolean extended, final boolean upgraded) {
            this.token = token;
            this.extended = extended;
            this.upgraded = upgraded;
        }

        public String token() {
            return this.token;
        }

        public boolean extended() {
            return this.extended;
        }

        public boolean upgraded() {
            return this.upgraded;
        }

        static PotionMode fromToken(final String token) {
            return java.util.Arrays.stream(values())
                .filter(value -> value.token.equalsIgnoreCase(token))
                .findFirst()
                .orElse(null);
        }
    }
}
