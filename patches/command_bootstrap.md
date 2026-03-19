# wild_economy Phase 1 Catalog Generator

Below are complete starter files for the Phase 1 catalog generator scaffold.

This slice covers:

* valid-item scan from Bukkit `Material`
* worth lookup/import
* top-level category assignment
* default policy suggestion
* generated catalog writing

This intentionally does **not** include recipe graph / derivation depth yet.

---

## File: `src/main/java/com/splatage/wild_economy/catalog/model/CatalogCategory.java`

```java
package com.splatage.wild_economy.catalog.model;

public enum CatalogCategory {
    WOODS,
    STONE,
    ORES_AND_MINERALS,
    FARMING,
    FOOD,
    MOB_DROPS,
    NETHER,
    END,
    REDSTONE,
    DECORATION,
    TOOLS,
    COMBAT,
    BREWING,
    TRANSPORT,
    MISC
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/model/CatalogPolicy.java`

```java
package com.splatage.wild_economy.catalog.model;

public enum CatalogPolicy {
    ALWAYS_AVAILABLE,
    EXCHANGE,
    SELL_ONLY,
    DISABLED
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/model/ItemFacts.java`

```java
package com.splatage.wild_economy.catalog.model;

import org.bukkit.Material;

public record ItemFacts(
    Material material,
    String key,
    boolean isItem,
    boolean isBlock,
    boolean stackable,
    int maxStackSize,
    boolean edible,
    boolean fuelCandidate,
    boolean hasWorthEntry
) {
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/model/GeneratedCatalogEntry.java`

```java
package com.splatage.wild_economy.catalog.model;

import java.math.BigDecimal;

public record GeneratedCatalogEntry(
    String key,
    CatalogCategory category,
    CatalogPolicy policy,
    boolean worthPresent,
    BigDecimal basePrice,
    String includeReason,
    String excludeReason,
    String notes
) {
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/model/CatalogGenerationResult.java`

```java
package com.splatage.wild_economy.catalog.model;

import java.util.List;

public record CatalogGenerationResult(
    List<GeneratedCatalogEntry> entries,
    int totalScanned,
    int totalGenerated,
    int totalDisabled,
    int missingWorthCount
) {
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/scan/MaterialScanner.java`

```java
package com.splatage.wild_economy.catalog.scan;

import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.List;

public interface MaterialScanner {
    List<ItemFacts> scanAll();
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/scan/BukkitMaterialScanner.java`

```java
package com.splatage.wild_economy.catalog.scan;

import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.worth.WorthPriceLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;

public final class BukkitMaterialScanner implements MaterialScanner {

    private final WorthPriceLookup worthPriceLookup;

    public BukkitMaterialScanner(final WorthPriceLookup worthPriceLookup) {
        this.worthPriceLookup = worthPriceLookup;
    }

    @Override
    public List<ItemFacts> scanAll() {
        final List<ItemFacts> results = new ArrayList<>();

        for (final Material material : Material.values()) {
            if (!isIncludedMaterial(material)) {
                continue;
            }

            final String key = normalizeKey(material);
            final boolean hasWorthEntry = this.worthPriceLookup.findPrice(key).isPresent();

            results.add(new ItemFacts(
                material,
                key,
                material.isItem(),
                material.isBlock(),
                material.getMaxStackSize() > 1,
                material.getMaxStackSize(),
                material.isEdible(),
                material.getBurnTime() > 0,
                hasWorthEntry
            ));
        }

        results.sort(Comparator.comparing(ItemFacts::key));
        return List.copyOf(results);
    }

    private boolean isIncludedMaterial(final Material material) {
        if (material == Material.AIR) {
            return false;
        }
        if (!material.isItem()) {
            return false;
        }
        return !material.isLegacy();
    }

    public static String normalizeKey(final Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/worth/WorthPriceLookup.java`

```java
package com.splatage.wild_economy.catalog.worth;

import java.math.BigDecimal;
import java.util.Optional;

public interface WorthPriceLookup {
    Optional<BigDecimal> findPrice(String itemKey);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/worth/WorthImporter.java`

```java
package com.splatage.wild_economy.catalog.worth;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WorthImporter implements WorthPriceLookup {

    private final Map<String, BigDecimal> pricesByKey;

    private WorthImporter(final Map<String, BigDecimal> pricesByKey) {
        this.pricesByKey = Map.copyOf(pricesByKey);
    }

    public static WorthImporter empty() {
        return new WorthImporter(Collections.emptyMap());
    }

    public static WorthImporter fromFile(final File worthFile) throws IOException {
        if (worthFile == null) {
            throw new IllegalArgumentException("worthFile must not be null");
        }
        if (!worthFile.exists() || !worthFile.isFile()) {
            throw new IOException("Worth file does not exist: " + worthFile.getAbsolutePath());
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(worthFile);
        final ConfigurationSection worthSection = yaml.getConfigurationSection("worth");
        if (worthSection == null) {
            return empty();
        }

        final Map<String, BigDecimal> prices = new LinkedHashMap<>();
        loadSectionRecursive(worthSection, "", prices);
        return new WorthImporter(prices);
    }

    @Override
    public Optional<BigDecimal> findPrice(final String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.pricesByKey.get(normalizeKey(itemKey)));
    }

    public int size() {
        return this.pricesByKey.size();
    }

    public Set<String> keys() {
        return this.pricesByKey.keySet();
    }

    private static void loadSectionRecursive(
        final ConfigurationSection section,
        final String prefix,
        final Map<String, BigDecimal> output
    ) {
        for (final String childKey : section.getKeys(false)) {
            final Object value = section.get(childKey);
            final String fullKey = prefix.isEmpty() ? childKey : prefix + "." + childKey;

            if (value instanceof ConfigurationSection childSection) {
                loadSectionRecursive(childSection, fullKey, output);
                continue;
            }

            final BigDecimal price = parseDecimal(value);
            if (price == null) {
                continue;
            }

            output.put(normalizeKey(fullKey), price);
        }
    }

    private static BigDecimal parseDecimal(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String stringValue) {
            final String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(trimmed);
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static String normalizeKey(final String rawKey) {
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return key.replace('-', '_');
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/classify/CategoryClassifier.java`

```java
package com.splatage.wild_economy.catalog.classify;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;

public interface CategoryClassifier {
    CatalogCategory classify(ItemFacts facts);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/classify/DefaultCategoryClassifier.java`

```java
package com.splatage.wild_economy.catalog.classify;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.Set;

public final class DefaultCategoryClassifier implements CategoryClassifier {

    private static final Set<String> FOOD_EXACT = Set.of(
        "apple",
        "bread",
        "cookie",
        "cake",
        "pumpkin_pie",
        "beetroot_soup",
        "mushroom_stew",
        "rabbit_stew",
        "suspicious_stew",
        "golden_apple",
        "enchanted_golden_apple",
        "dried_kelp",
        "honey_bottle"
    );

    private static final Set<String> FARMING_EXACT = Set.of(
        "wheat",
        "carrot",
        "potato",
        "beetroot",
        "melon_slice",
        "pumpkin",
        "melon",
        "sugar_cane",
        "bamboo",
        "cactus",
        "kelp",
        "sweet_berries",
        "glow_berries",
        "nether_wart"
    );

    private static final Set<String> MOB_DROP_EXACT = Set.of(
        "rotten_flesh",
        "bone",
        "arrow",
        "string",
        "spider_eye",
        "fermented_spider_eye",
        "gunpowder",
        "slime_ball",
        "magma_cream",
        "ender_pearl",
        "ghast_tear",
        "phantom_membrane",
        "feather",
        "leather",
        "ink_sac",
        "glow_ink_sac",
        "rabbit_hide",
        "prismarine_shard",
        "prismarine_crystals",
        "shulker_shell"
    );

    @Override
    public CatalogCategory classify(final ItemFacts facts) {
        final String key = facts.key();

        if (isWood(key)) {
            return CatalogCategory.WOODS;
        }
        if (isStone(key)) {
            return CatalogCategory.STONE;
        }
        if (isOreOrMineral(key)) {
            return CatalogCategory.ORES_AND_MINERALS;
        }
        if (isFarming(key)) {
            return CatalogCategory.FARMING;
        }
        if (isFood(key, facts.edible())) {
            return CatalogCategory.FOOD;
        }
        if (isMobDrop(key)) {
            return CatalogCategory.MOB_DROPS;
        }
        if (isNether(key)) {
            return CatalogCategory.NETHER;
        }
        if (isEnd(key)) {
            return CatalogCategory.END;
        }
        if (isRedstone(key)) {
            return CatalogCategory.REDSTONE;
        }
        if (isBrewing(key)) {
            return CatalogCategory.BREWING;
        }
        if (isTool(key)) {
            return CatalogCategory.TOOLS;
        }
        if (isCombat(key)) {
            return CatalogCategory.COMBAT;
        }
        if (isTransport(key)) {
            return CatalogCategory.TRANSPORT;
        }
        if (isDecoration(key)) {
            return CatalogCategory.DECORATION;
        }

        return CatalogCategory.MISC;
    }

    private boolean isWood(final String key) {
        return key.endsWith("_log")
            || key.endsWith("_wood")
            || key.endsWith("_planks")
            || key.endsWith("_sapling")
            || key.endsWith("_leaves")
            || key.endsWith("_hanging_sign")
            || key.endsWith("_sign")
            || key.endsWith("_boat")
            || key.endsWith("_chest_boat")
            || key.contains("mangrove")
            || key.contains("bamboo_block")
            || key.contains("bamboo_planks")
            || key.contains("stripped_");
    }

    private boolean isStone(final String key) {
        return key.contains("stone")
            || key.contains("cobble")
            || key.contains("deepslate")
            || key.contains("tuff")
            || key.contains("andesite")
            || key.contains("granite")
            || key.contains("diorite")
            || key.contains("blackstone")
            || key.contains("basalt")
            || key.contains("calcite")
            || key.contains("dripstone")
            || key.contains("brick")
            || key.equals("smooth_quartz")
            || key.equals("quartz_bricks");
    }

    private boolean isOreOrMineral(final String key) {
        return key.contains("_ore")
            || key.startsWith("raw_")
            || key.endsWith("_ingot")
            || key.endsWith("_nugget")
            || key.equals("coal")
            || key.equals("charcoal")
            || key.equals("diamond")
            || key.equals("emerald")
            || key.equals("lapis_lazuli")
            || key.equals("redstone")
            || key.equals("amethyst_shard")
            || key.equals("quartz")
            || key.equals("netherite_scrap")
            || key.equals("netherite_ingot")
            || key.endsWith("_block") && (
                key.startsWith("iron_")
                    || key.startsWith("gold_")
                    || key.startsWith("diamond_")
                    || key.startsWith("emerald_")
                    || key.startsWith("lapis_")
                    || key.startsWith("redstone_")
                    || key.startsWith("copper_")
                    || key.startsWith("coal_")
                    || key.startsWith("amethyst_")
                    || key.startsWith("netherite_")
            );
    }

    private boolean isFarming(final String key) {
        return FARMING_EXACT.contains(key)
            || key.endsWith("_seeds")
            || key.equals("wheat_seeds")
            || key.equals("beetroot_seeds")
            || key.equals("melon_seeds")
            || key.equals("pumpkin_seeds")
            || key.equals("torchflower_seeds")
            || key.equals("pitcher_pod")
            || key.contains("crop")
            || key.contains("farmland")
            || key.contains("cocoa")
            || key.contains("vine")
            || key.contains("propagule");
    }

    private boolean isFood(final String key, final boolean edible) {
        return edible
            || FOOD_EXACT.contains(key)
            || key.startsWith("cooked_")
            || key.startsWith("raw_")
            || key.endsWith("_meat")
            || key.endsWith("_stew")
            || key.endsWith("_slice")
            || key.endsWith("_berries")
            || key.equals("egg");
    }

    private boolean isMobDrop(final String key) {
        return MOB_DROP_EXACT.contains(key)
            || key.endsWith("_spawn_egg");
    }

    private boolean isNether(final String key) {
        return key.contains("nether")
            || key.contains("blaze")
            || key.contains("ghast")
            || key.contains("magma")
            || key.contains("netherrack")
            || key.contains("crimson")
            || key.contains("warped")
            || key.contains("soul_")
            || key.contains("blackstone")
            || key.contains("ancient_debris")
            || key.contains("shroomlight")
            || key.contains("glowstone");
    }

    private boolean isEnd(final String key) {
        return key.contains("end_")
            || key.contains("chorus")
            || key.contains("purpur")
            || key.contains("shulker")
            || key.contains("elytra")
            || key.contains("dragon_");
    }

    private boolean isRedstone(final String key) {
        return key.equals("redstone")
            || key.contains("repeater")
            || key.contains("comparator")
            || key.contains("observer")
            || key.contains("piston")
            || key.contains("hopper")
            || key.contains("dropper")
            || key.contains("dispenser")
            || key.contains("daylight_detector")
            || key.contains("target")
            || key.contains("lectern")
            || key.contains("tripwire")
            || key.contains("pressure_plate")
            || key.contains("redstone_lamp")
            || key.contains("sculk_sensor")
            || key.contains("calibrated_sculk_sensor");
    }

    private boolean isBrewing(final String key) {
        return key.contains("potion")
            || key.contains("brewing")
            || key.contains("cauldron")
            || key.contains("blaze_powder")
            || key.contains("ghast_tear")
            || key.contains("glistering_melon")
            || key.contains("nether_wart")
            || key.contains("dragon_breath")
            || key.contains("phantom_membrane");
    }

    private boolean isTool(final String key) {
        return key.endsWith("_pickaxe")
            || key.endsWith("_axe")
            || key.endsWith("_shovel")
            || key.endsWith("_hoe")
            || key.endsWith("_shears")
            || key.endsWith("_bucket")
            || key.equals("flint_and_steel")
            || key.equals("fishing_rod")
            || key.equals("carrot_on_a_stick")
            || key.equals("warped_fungus_on_a_stick")
            || key.equals("brush")
            || key.equals("spyglass")
            || key.equals("clock")
            || key.equals("compass")
            || key.equals("recovery_compass");
    }

    private boolean isCombat(final String key) {
        return key.endsWith("_sword")
            || key.endsWith("_helmet")
            || key.endsWith("_chestplate")
            || key.endsWith("_leggings")
            || key.endsWith("_boots")
            || key.equals("bow")
            || key.equals("crossbow")
            || key.equals("trident")
            || key.equals("shield")
            || key.equals("arrow")
            || key.equals("spectral_arrow")
            || key.equals("tipped_arrow")
            || key.contains("mace");
    }

    private boolean isTransport(final String key) {
        return key.contains("minecart")
            || key.contains("rail")
            || key.contains("boat")
            || key.equals("saddle")
            || key.equals("lead");
    }

    private boolean isDecoration(final String key) {
        return key.contains("banner")
            || key.contains("bed")
            || key.contains("carpet")
            || key.contains("painting")
            || key.contains("flower_pot")
            || key.contains("candle")
            || key.contains("lantern")
            || key.contains("glass")
            || key.contains("terracotta")
            || key.contains("concrete")
            || key.contains("coral")
            || key.contains("pottery_sherd")
            || key.contains("head")
            || key.contains("skull")
            || key.contains("frame")
            || key.contains("amethyst_cluster")
            || key.contains("sea_pickle");
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/policy/PolicySuggestionService.java`

```java
package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.math.BigDecimal;

public interface PolicySuggestionService {
    CatalogPolicy suggest(ItemFacts facts, CatalogCategory category, BigDecimal basePrice);

    String includeReason(ItemFacts facts, CatalogCategory category, BigDecimal basePrice, CatalogPolicy policy);

    String excludeReason(ItemFacts facts, CatalogCategory category, BigDecimal basePrice, CatalogPolicy policy);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/policy/DefaultPolicySuggestionService.java`

```java
package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.math.BigDecimal;
import java.util.Set;

public final class DefaultPolicySuggestionService implements PolicySuggestionService {

    private static final Set<String> ALWAYS_AVAILABLE = Set.of(
        "sand",
        "red_sand",
        "gravel",
        "ice",
        "packed_ice",
        "blue_ice",
        "oak_log",
        "spruce_log",
        "birch_log",
        "jungle_log",
        "acacia_log",
        "dark_oak_log",
        "mangrove_log",
        "cherry_log",
        "crimson_stem",
        "warped_stem"
    );

    private static final Set<String> HARD_DISABLED_EXACT = Set.of(
        "air",
        "knowledge_book",
        "command_block",
        "chain_command_block",
        "repeating_command_block",
        "command_block_minecart",
        "jigsaw",
        "structure_block",
        "structure_void",
        "light",
        "barrier",
        "debug_stick",
        "written_book"
    );

    @Override
    public CatalogPolicy suggest(final ItemFacts facts, final CatalogCategory category, final BigDecimal basePrice) {
        final String key = facts.key();

        if (isHardDisabled(key)) {
            return CatalogPolicy.DISABLED;
        }
        if (ALWAYS_AVAILABLE.contains(key)) {
            return CatalogPolicy.ALWAYS_AVAILABLE;
        }
        if (facts.hasWorthEntry()) {
            return CatalogPolicy.EXCHANGE;
        }
        return CatalogPolicy.DISABLED;
    }

    @Override
    public String includeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final BigDecimal basePrice,
        final CatalogPolicy policy
    ) {
        return switch (policy) {
            case ALWAYS_AVAILABLE -> "always-available allowlist";
            case EXCHANGE -> facts.hasWorthEntry()
                ? "worth-present default exchange"
                : "policy override required";
            case SELL_ONLY -> "sell-only fallback";
            case DISABLED -> "";
        };
    }

    @Override
    public String excludeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final BigDecimal basePrice,
        final CatalogPolicy policy
    ) {
        if (policy != CatalogPolicy.DISABLED) {
            return "";
        }
        if (isHardDisabled(facts.key())) {
            return "hard-disabled non-standard or admin item";
        }
        if (!facts.hasWorthEntry()) {
            return "missing worth entry";
        }
        return "disabled by policy rules";
    }

    private boolean isHardDisabled(final String key) {
        return HARD_DISABLED_EXACT.contains(key) || key.endsWith("_spawn_egg");
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/generate/CatalogGenerationService.java`

```java
package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.CategoryClassifier;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.GeneratedCatalogEntry;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.policy.PolicySuggestionService;
import com.splatage.wild_economy.catalog.scan.MaterialScanner;
import com.splatage.wild_economy.catalog.worth.WorthPriceLookup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CatalogGenerationService {

    private final MaterialScanner materialScanner;
    private final WorthPriceLookup worthPriceLookup;
    private final CategoryClassifier categoryClassifier;
    private final PolicySuggestionService policySuggestionService;

    public CatalogGenerationService(
        final MaterialScanner materialScanner,
        final WorthPriceLookup worthPriceLookup,
        final CategoryClassifier categoryClassifier,
        final PolicySuggestionService policySuggestionService
    ) {
        this.materialScanner = materialScanner;
        this.worthPriceLookup = worthPriceLookup;
        this.categoryClassifier = categoryClassifier;
        this.policySuggestionService = policySuggestionService;
    }

    public CatalogGenerationResult generate() {
        final List<ItemFacts> scanned = this.materialScanner.scanAll();
        final List<GeneratedCatalogEntry> generated = new ArrayList<>(scanned.size());

        int disabledCount = 0;
        int missingWorthCount = 0;

        for (final ItemFacts facts : scanned) {
            final CatalogCategory category = this.categoryClassifier.classify(facts);
            final BigDecimal basePrice = this.worthPriceLookup.findPrice(facts.key()).orElse(null);
            final CatalogPolicy policy = this.policySuggestionService.suggest(facts, category, basePrice);
            final String includeReason = this.policySuggestionService.includeReason(facts, category, basePrice, policy);
            final String excludeReason = this.policySuggestionService.excludeReason(facts, category, basePrice, policy);

            if (policy == CatalogPolicy.DISABLED) {
                disabledCount++;
            }
            if (!facts.hasWorthEntry()) {
                missingWorthCount++;
            }

            generated.add(new GeneratedCatalogEntry(
                facts.key(),
                category,
                policy,
                facts.hasWorthEntry(),
                basePrice,
                includeReason,
                excludeReason,
                buildNotes(facts)
            ));
        }

        generated.sort(
            Comparator.comparing(GeneratedCatalogEntry::category)
                .thenComparing(GeneratedCatalogEntry::key)
        );

        return new CatalogGenerationResult(
            List.copyOf(generated),
            scanned.size(),
            generated.size(),
            disabledCount,
            missingWorthCount
        );
    }

    private String buildNotes(final ItemFacts facts) {
        final List<String> notes = new ArrayList<>(4);
        if (facts.isBlock()) {
            notes.add("block-item");
        }
        if (facts.edible()) {
            notes.add("edible");
        }
        if (facts.maxStackSize() == 1) {
            notes.add("unstackable");
        }
        if (facts.fuelCandidate()) {
            notes.add("fuel");
        }
        return String.join(", ", notes);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/generate/CatalogFileWriter.java`

```java
package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.model.GeneratedCatalogEntry;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CatalogFileWriter {

    private final JavaPlugin plugin;

    public CatalogFileWriter(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File writeGeneratedCatalog(final CatalogGenerationResult result) throws IOException {
        final File generatedDir = new File(this.plugin.getDataFolder(), "generated");
        if (!generatedDir.exists() && !generatedDir.mkdirs()) {
            throw new IOException("Failed to create generated directory: " + generatedDir.getAbsolutePath());
        }

        final File catalogFile = new File(generatedDir, "generated-catalog.yml");
        final YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("generated-at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        yaml.set("summary.total-scanned", result.totalScanned());
        yaml.set("summary.total-generated", result.totalGenerated());
        yaml.set("summary.total-disabled", result.totalDisabled());
        yaml.set("summary.missing-worth-count", result.missingWorthCount());

        for (final GeneratedCatalogEntry entry : result.entries()) {
            final String path = "entries." + entry.key();
            yaml.set(path + ".category", entry.category().name());
            yaml.set(path + ".policy", entry.policy().name());
            yaml.set(path + ".worth-present", entry.worthPresent());
            yaml.set(path + ".base-price", entry.basePrice() != null ? entry.basePrice().toPlainString() : null);
            yaml.set(path + ".include-reason", entry.includeReason());
            yaml.set(path + ".exclude-reason", entry.excludeReason());
            yaml.set(path + ".notes", entry.notes());
        }

        yaml.save(catalogFile);
        return catalogFile;
    }

    public File writeSummary(final CatalogGenerationResult result) throws IOException {
        final File generatedDir = new File(this.plugin.getDataFolder(), "generated");
        if (!generatedDir.exists() && !generatedDir.mkdirs()) {
            throw new IOException("Failed to create generated directory: " + generatedDir.getAbsolutePath());
        }

        final File summaryFile = new File(generatedDir, "generated-catalog-summary.yml");
        final YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("generated-at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        yaml.set("total-scanned", result.totalScanned());
        yaml.set("total-generated", result.totalGenerated());
        yaml.set("total-disabled", result.totalDisabled());
        yaml.set("missing-worth-count", result.missingWorthCount());

        yaml.save(summaryFile);
        return summaryFile;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/generate/CatalogGeneratorFacade.java`

```java
package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.DefaultCategoryClassifier;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.policy.DefaultPolicySuggestionService;
import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import com.splatage.wild_economy.catalog.worth.WorthImporter;
import java.io.File;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

public final class CatalogGeneratorFacade {

    private final JavaPlugin plugin;

    public CatalogGeneratorFacade(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CatalogGenerationResult generateFromWorthFile(final File worthFile) throws IOException {
        final WorthImporter worthImporter = WorthImporter.fromFile(worthFile);
        final CatalogGenerationService service = new CatalogGenerationService(
            new BukkitMaterialScanner(worthImporter),
            worthImporter,
            new DefaultCategoryClassifier(),
            new DefaultPolicySuggestionService()
        );

        return service.generate();
    }

    public void writeOutputs(final CatalogGenerationResult result) throws IOException {
        final CatalogFileWriter writer = new CatalogFileWriter(this.plugin);
        writer.writeGeneratedCatalog(result);
        writer.writeSummary(result);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/generate/CatalogGenerationReportFormatter.java`

```java
package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;

public final class CatalogGenerationReportFormatter {

    private CatalogGenerationReportFormatter() {
    }

    public static String formatSingleLine(final CatalogGenerationResult result) {
        return "generated=" + result.totalGenerated()
            + ", scanned=" + result.totalScanned()
            + ", disabled=" + result.totalDisabled()
            + ", missingWorth=" + result.missingWorthCount();
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/README_PHASE1_NOTES.md`

```md
# Phase 1 Catalog Generator Notes

This phase intentionally keeps the generator simple.

Included:
- scan Bukkit `Material` item universe
- normalize item keys to lowercase snake_case matching Bukkit material names
- import prices from Essentials `worth.yml`
- classify top-level category
- suggest default policy
- write generated catalog YAML

Not yet included:
- recipe graph
- derivation depth
- root/basic item configuration
- override merge
- command wiring

## Current default policy logic

1. hard-disabled items -> `DISABLED`
2. explicit always-available allowlist -> `ALWAYS_AVAILABLE`
3. remaining items with worth -> `EXCHANGE`
4. everything else -> `DISABLED`

This matches the current v1 testing goal:

> If a valid standard item is present in `worth.yml`, default it into the Exchange unless a hard exclusion says otherwise.

## Intended next phase

Phase 2 adds:
- recipe graph extraction from Bukkit recipes
- minimum derivation depth from configured root/basic items
- depth-based inclusion/exclusion to reduce clutter
- overrides merged last
```

---

## Suggested wiring example

This is intentionally not presented as a locked file because your plugin bootstrap/command layout has not been pasted yet.

Use the facade somewhere in your admin flow roughly like this:

```java
final CatalogGeneratorFacade facade = new CatalogGeneratorFacade(plugin);
final CatalogGenerationResult result = facade.generateFromWorthFile(new File(plugin.getDataFolder(), "worth.yml"));
facade.writeOutputs(result);
plugin.getLogger().info("Catalog generation complete: " + CatalogGenerationReportFormatter.formatSingleLine(result));
```

---

## Notes

* This scaffold is deliberately conservative and repo-neutral.
* The category classifier is heuristic and meant to be overrideable later.
* The policy suggester is aligned to the current `wild_economy` direction, not final economy design.
* The next proper implementation slice after this should be override merge, then recipe-depth derivation.
