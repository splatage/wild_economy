# wild_economy Phase 2 Derivation Scaffold

This canvas contains the **Phase 2 catalog-generator scaffold** for rooted recipe derivation.

It assumes the following locked design:

* `root-values.yml` contains the only economic anchors.
* Only anchored items may seed derivation.
* A missing root blocks that derivation path.
* An item is allowed if **at least one** complete recipe path resolves from anchored roots within the allowed depth.
* `exchange-items.yml` is intended to become the override layer later.

This phase focuses on the **generator only**.
It does **not** yet wire generated catalog as the runtime shop source.

---

## File: `src/main/java/com/splatage/wild_economy/catalog/derive/DerivationReason.java`

```java
package com.splatage.wild_economy.catalog.derive;

public enum DerivationReason {
    ROOT_ANCHOR,
    DERIVED_FROM_ROOT,
    DEPTH_LIMIT,
    ALL_PATHS_BLOCKED,
    NO_RECIPE_AND_NO_ROOT,
    CYCLE_DETECTED,
    HARD_DISABLED
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/derive/DerivedItemResult.java`

```java
package com.splatage.wild_economy.catalog.derive;

import java.math.BigDecimal;

public record DerivedItemResult(
    boolean included,
    boolean rootValuePresent,
    BigDecimal rootValue,
    Integer derivationDepth,
    BigDecimal derivedValue,
    DerivationReason reason
) {

    public static DerivedItemResult rootAnchor(final BigDecimal rootValue) {
        return new DerivedItemResult(
            true,
            true,
            rootValue,
            0,
            rootValue,
            DerivationReason.ROOT_ANCHOR
        );
    }

    public static DerivedItemResult derived(
        final int derivationDepth,
        final BigDecimal derivedValue
    ) {
        return new DerivedItemResult(
            true,
            false,
            null,
            derivationDepth,
            derivedValue,
            DerivationReason.DERIVED_FROM_ROOT
        );
    }

    public static DerivedItemResult blocked(
        final Integer derivationDepth,
        final BigDecimal derivedValue,
        final DerivationReason reason
    ) {
        return new DerivedItemResult(
            false,
            false,
            null,
            derivationDepth,
            derivedValue,
            reason
        );
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/recipe/RecipeIngredient.java`

```java
package com.splatage.wild_economy.catalog.recipe;

public record RecipeIngredient(
    String itemKey,
    int amount
) {
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/recipe/RecipeDefinition.java`

```java
package com.splatage.wild_economy.catalog.recipe;

import java.util.List;

public record RecipeDefinition(
    String outputKey,
    int outputAmount,
    String recipeType,
    List<RecipeIngredient> ingredients
) {
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/recipe/RecipeGraph.java`

```java
package com.splatage.wild_economy.catalog.recipe;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class RecipeGraph {

    private final Map<String, List<RecipeDefinition>> recipesByOutputKey;

    public RecipeGraph(final Map<String, List<RecipeDefinition>> recipesByOutputKey) {
        this.recipesByOutputKey = Map.copyOf(recipesByOutputKey);
    }

    public List<RecipeDefinition> getRecipesFor(final String outputKey) {
        return this.recipesByOutputKey.getOrDefault(outputKey, Collections.emptyList());
    }

    public int recipeOutputCount() {
        return this.recipesByOutputKey.size();
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/recipe/BukkitRecipeGraphBuilder.java`

```java
package com.splatage.wild_economy.catalog.recipe;

import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

public final class BukkitRecipeGraphBuilder {

    private static final int MAX_COMBINATIONS_PER_RECIPE = 128;

    public RecipeGraph build() {
        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();
        final Iterator<Recipe> iterator = Bukkit.getServer().recipeIterator();

        while (iterator.hasNext()) {
            final Recipe recipe = iterator.next();
            final ItemStack result = recipe.getResult();
            if (result == null || result.getType() == null || result.getType() == Material.AIR) {
                continue;
            }

            final String outputKey = BukkitMaterialScanner.normalizeKey(result.getType());
            final int outputAmount = Math.max(1, result.getAmount());
            final List<RecipeDefinition> extracted = this.extractRecipeDefinitions(recipe, outputKey, outputAmount);
            if (extracted.isEmpty()) {
                continue;
            }

            recipesByOutput.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).addAll(extracted);
        }

        final Map<String, List<RecipeDefinition>> frozen = new LinkedHashMap<>();
        for (final Map.Entry<String, List<RecipeDefinition>> entry : recipesByOutput.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return new RecipeGraph(frozen);
    }

    private List<RecipeDefinition> extractRecipeDefinitions(
        final Recipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return this.extractShapedRecipes(shapedRecipe, outputKey, outputAmount);
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            return this.extractShapelessRecipes(shapelessRecipe, outputKey, outputAmount);
        }
        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            return this.extractSingleInputRecipe(
                outputKey,
                outputAmount,
                "stonecutting",
                stonecuttingRecipe.getInputChoice()
            );
        }
        if (recipe instanceof CookingRecipe<?> cookingRecipe) {
            return this.extractSingleInputRecipe(
                outputKey,
                outputAmount,
                recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT),
                cookingRecipe.getInputChoice()
            );
        }

        return Collections.emptyList();
    }

    private List<RecipeDefinition> extractShapedRecipes(
        final ShapedRecipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        final Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        final List<List<String>> slotOptions = new ArrayList<>();

        for (final String row : recipe.getShape()) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.length(); i++) {
                final char key = row.charAt(i);
                if (key == ' ') {
                    continue;
                }
                final RecipeChoice choice = choiceMap.get(key);
                final List<String> options = this.resolveChoiceOptions(choice);
                if (options.isEmpty()) {
                    return Collections.emptyList();
                }
                slotOptions.add(options);
            }
        }

        return this.expandRecipes(outputKey, outputAmount, "shaped", slotOptions);
    }

    private List<RecipeDefinition> extractShapelessRecipes(
        final ShapelessRecipe recipe,
        final String outputKey,
        final int outputAmount
    ) {
        final List<List<String>> slotOptions = new ArrayList<>();
        for (final RecipeChoice choice : recipe.getChoiceList()) {
            final List<String> options = this.resolveChoiceOptions(choice);
            if (options.isEmpty()) {
                return Collections.emptyList();
            }
            slotOptions.add(options);
        }

        return this.expandRecipes(outputKey, outputAmount, "shapeless", slotOptions);
    }

    private List<RecipeDefinition> extractSingleInputRecipe(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final RecipeChoice inputChoice
    ) {
        final List<String> options = this.resolveChoiceOptions(inputChoice);
        if (options.isEmpty()) {
            return Collections.emptyList();
        }

        final List<RecipeDefinition> definitions = new ArrayList<>(options.size());
        for (final String option : options) {
            definitions.add(new RecipeDefinition(
                outputKey,
                outputAmount,
                recipeType,
                List.of(new RecipeIngredient(option, 1))
            ));
        }
        return List.copyOf(definitions);
    }

    private List<String> resolveChoiceOptions(final RecipeChoice choice) {
        if (choice == null) {
            return Collections.emptyList();
        }
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            final List<String> keys = new ArrayList<>();
            for (final Material material : materialChoice.getChoices()) {
                if (material == null || material == Material.AIR || !material.isItem()) {
                    continue;
                }
                keys.add(BukkitMaterialScanner.normalizeKey(material));
            }
            return List.copyOf(keys);
        }

        return Collections.emptyList();
    }

    private List<RecipeDefinition> expandRecipes(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final List<List<String>> slotOptions
    ) {
        if (slotOptions.isEmpty()) {
            return Collections.emptyList();
        }

        long combinations = 1L;
        for (final List<String> options : slotOptions) {
            combinations *= Math.max(1, options.size());
            if (combinations > MAX_COMBINATIONS_PER_RECIPE) {
                return Collections.emptyList();
            }
        }

        final List<RecipeDefinition> definitions = new ArrayList<>();
        this.expandRecipeSlots(outputKey, outputAmount, recipeType, slotOptions, 0, new ArrayList<>(), definitions);
        return List.copyOf(definitions);
    }

    private void expandRecipeSlots(
        final String outputKey,
        final int outputAmount,
        final String recipeType,
        final List<List<String>> slotOptions,
        final int slotIndex,
        final List<String> currentSelection,
        final List<RecipeDefinition> output
    ) {
        if (slotIndex >= slotOptions.size()) {
            output.add(new RecipeDefinition(
                outputKey,
                outputAmount,
                recipeType,
                this.collapseSelection(currentSelection)
            ));
            return;
        }

        for (final String option : slotOptions.get(slotIndex)) {
            currentSelection.add(option);
            this.expandRecipeSlots(outputKey, outputAmount, recipeType, slotOptions, slotIndex + 1, currentSelection, output);
            currentSelection.remove(currentSelection.size() - 1);
        }
    }

    private List<RecipeIngredient> collapseSelection(final List<String> selection) {
        final Map<String, Integer> counts = new HashMap<>();
        for (final String key : selection) {
            counts.merge(key, 1, Integer::sum);
        }

        final List<RecipeIngredient> ingredients = new ArrayList<>(counts.size());
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            ingredients.add(new RecipeIngredient(entry.getKey(), entry.getValue()));
        }
        ingredients.sort(java.util.Comparator.comparing(RecipeIngredient::itemKey));
        return List.copyOf(ingredients);
    }
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
    boolean hasRootValue
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
    boolean rootValuePresent,
    BigDecimal rootValue,
    Integer derivationDepth,
    BigDecimal derivedValue,
    String derivationReason,
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
    int rootAnchoredCount,
    int derivedIncludedCount
) {
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/derive/RootAnchoredDerivationService.java`

```java
package com.splatage.wild_economy.catalog.derive;

import com.splatage.wild_economy.catalog.recipe.RecipeDefinition;
import com.splatage.wild_economy.catalog.recipe.RecipeGraph;
import com.splatage.wild_economy.catalog.recipe.RecipeIngredient;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RootAnchoredDerivationService {

    private final RecipeGraph recipeGraph;
    private final RootValueLookup rootValueLookup;
    private final int maxDerivationDepth;
    private final Map<String, DerivedItemResult> cache = new HashMap<>();
    private final Set<String> visiting = new HashSet<>();

    public RootAnchoredDerivationService(
        final RecipeGraph recipeGraph,
        final RootValueLookup rootValueLookup,
        final int maxDerivationDepth
    ) {
        this.recipeGraph = recipeGraph;
        this.rootValueLookup = rootValueLookup;
        this.maxDerivationDepth = maxDerivationDepth;
    }

    public DerivedItemResult resolve(final String itemKey) {
        final DerivedItemResult cached = this.cache.get(itemKey);
        if (cached != null) {
            return cached;
        }

        if (this.visiting.contains(itemKey)) {
            return DerivedItemResult.blocked(null, null, DerivationReason.CYCLE_DETECTED);
        }

        final BigDecimal rootValue = this.rootValueLookup.findRootValue(itemKey).orElse(null);
        if (rootValue != null) {
            final DerivedItemResult result = DerivedItemResult.rootAnchor(rootValue);
            this.cache.put(itemKey, result);
            return result;
        }

        this.visiting.add(itemKey);
        try {
            final List<RecipeDefinition> recipes = this.recipeGraph.getRecipesFor(itemKey);
            if (recipes.isEmpty()) {
                final DerivedItemResult result = DerivedItemResult.blocked(
                    null,
                    null,
                    DerivationReason.NO_RECIPE_AND_NO_ROOT
                );
                this.cache.put(itemKey, result);
                return result;
            }

            final List<CandidatePath> validCandidates = new java.util.ArrayList<>();
            for (final RecipeDefinition recipe : recipes) {
                final CandidatePath candidate = this.evaluateRecipe(recipe);
                if (candidate != null) {
                    validCandidates.add(candidate);
                }
            }

            if (validCandidates.isEmpty()) {
                final DerivedItemResult result = DerivedItemResult.blocked(
                    null,
                    null,
                    DerivationReason.ALL_PATHS_BLOCKED
                );
                this.cache.put(itemKey, result);
                return result;
            }

            final CandidatePath best = validCandidates.stream()
                .min(
                    Comparator.comparingInt(CandidatePath::depth)
                        .thenComparing(CandidatePath::value)
                )
                .orElseThrow();

            final DerivedItemResult result;
            if (best.depth() > this.maxDerivationDepth) {
                result = DerivedItemResult.blocked(best.depth(), best.value(), DerivationReason.DEPTH_LIMIT);
            } else {
                result = DerivedItemResult.derived(best.depth(), best.value());
            }

            this.cache.put(itemKey, result);
            return result;
        } finally {
            this.visiting.remove(itemKey);
        }
    }

    private CandidatePath evaluateRecipe(final RecipeDefinition recipe) {
        BigDecimal totalIngredientValue = BigDecimal.ZERO;
        int maxIngredientDepth = 0;

        for (final RecipeIngredient ingredient : recipe.ingredients()) {
            final DerivedItemResult ingredientResult = this.resolve(ingredient.itemKey());
            if (!ingredientResult.included() || ingredientResult.derivedValue() == null || ingredientResult.derivationDepth() == null) {
                return null;
            }

            totalIngredientValue = totalIngredientValue.add(
                ingredientResult.derivedValue().multiply(BigDecimal.valueOf(ingredient.amount()))
            );
            maxIngredientDepth = Math.max(maxIngredientDepth, ingredientResult.derivationDepth());
        }

        final int outputAmount = Math.max(1, recipe.outputAmount());
        final int depth = maxIngredientDepth + 1;
        final BigDecimal value = totalIngredientValue
            .divide(BigDecimal.valueOf(outputAmount), 8, RoundingMode.HALF_UP)
            .stripTrailingZeros();

        return new CandidatePath(depth, value);
    }

    private record CandidatePath(int depth, BigDecimal value) {
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/policy/PolicySuggestionService.java`

```java
package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;

public interface PolicySuggestionService {
    CatalogPolicy suggest(ItemFacts facts, CatalogCategory category, DerivedItemResult derivation);

    String includeReason(ItemFacts facts, CatalogCategory category, DerivedItemResult derivation, CatalogPolicy policy);

    String excludeReason(ItemFacts facts, CatalogCategory category, DerivedItemResult derivation, CatalogPolicy policy);
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/policy/DefaultPolicySuggestionService.java`

```java
package com.splatage.wild_economy.catalog.policy;

import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
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
    public CatalogPolicy suggest(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation
    ) {
        final String key = facts.key();

        if (isHardDisabled(key)) {
            return CatalogPolicy.DISABLED;
        }
        if (!derivation.included()) {
            return CatalogPolicy.DISABLED;
        }
        if (ALWAYS_AVAILABLE.contains(key)) {
            return CatalogPolicy.ALWAYS_AVAILABLE;
        }
        return CatalogPolicy.EXCHANGE;
    }

    @Override
    public String includeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation,
        final CatalogPolicy policy
    ) {
        if (policy == CatalogPolicy.ALWAYS_AVAILABLE) {
            return "always-available allowlist";
        }
        if (policy == CatalogPolicy.EXCHANGE) {
            return switch (derivation.reason()) {
                case ROOT_ANCHOR -> "root-anchor";
                case DERIVED_FROM_ROOT -> "derived-from-root";
                default -> "policy override required";
            };
        }
        if (policy == CatalogPolicy.SELL_ONLY) {
            return "sell-only fallback";
        }
        return "";
    }

    @Override
    public String excludeReason(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation,
        final CatalogPolicy policy
    ) {
        if (policy != CatalogPolicy.DISABLED) {
            return "";
        }
        if (isHardDisabled(facts.key())) {
            return "hard-disabled non-standard or admin item";
        }
        return switch (derivation.reason()) {
            case DEPTH_LIMIT -> "depth-limit";
            case ALL_PATHS_BLOCKED -> "all-paths-blocked";
            case NO_RECIPE_AND_NO_ROOT -> "no-recipe-and-no-root";
            case CYCLE_DETECTED -> "cycle-detected";
            case HARD_DISABLED -> "hard-disabled";
            case ROOT_ANCHOR, DERIVED_FROM_ROOT -> "disabled by policy rules";
        };
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
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.derive.RootAnchoredDerivationService;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.GeneratedCatalogEntry;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.policy.PolicySuggestionService;
import com.splatage.wild_economy.catalog.scan.MaterialScanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CatalogGenerationService {

    private final MaterialScanner materialScanner;
    private final CategoryClassifier categoryClassifier;
    private final PolicySuggestionService policySuggestionService;
    private final RootAnchoredDerivationService derivationService;

    public CatalogGenerationService(
        final MaterialScanner materialScanner,
        final CategoryClassifier categoryClassifier,
        final PolicySuggestionService policySuggestionService,
        final RootAnchoredDerivationService derivationService
    ) {
        this.materialScanner = materialScanner;
        this.categoryClassifier = categoryClassifier;
        this.policySuggestionService = policySuggestionService;
        this.derivationService = derivationService;
    }

    public CatalogGenerationResult generate() {
        final List<ItemFacts> scanned = this.materialScanner.scanAll();
        final List<GeneratedCatalogEntry> generated = new ArrayList<>(scanned.size());

        int disabledCount = 0;
        int rootAnchoredCount = 0;
        int derivedIncludedCount = 0;

        for (final ItemFacts facts : scanned) {
            final CatalogCategory category = this.categoryClassifier.classify(facts);
            final DerivedItemResult derivation = this.derivationService.resolve(facts.key());
            final CatalogPolicy policy = this.policySuggestionService.suggest(facts, category, derivation);
            final String includeReason = this.policySuggestionService.includeReason(facts, category, derivation, policy);
            final String excludeReason = this.policySuggestionService.excludeReason(facts, category, derivation, policy);

            if (policy == CatalogPolicy.DISABLED) {
                disabledCount++;
            }
            if (derivation.reason() == com.splatage.wild_economy.catalog.derive.DerivationReason.ROOT_ANCHOR) {
                rootAnchoredCount++;
            }
            if (derivation.reason() == com.splatage.wild_economy.catalog.derive.DerivationReason.DERIVED_FROM_ROOT) {
                derivedIncludedCount++;
            }

            generated.add(new GeneratedCatalogEntry(
                facts.key(),
                category,
                policy,
                derivation.rootValuePresent(),
                derivation.rootValue(),
                derivation.derivationDepth(),
                derivation.derivedValue(),
                derivation.reason().name(),
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
            rootAnchoredCount,
            derivedIncludedCount
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
        yaml.set("summary.root-anchored-count", result.rootAnchoredCount());
        yaml.set("summary.derived-included-count", result.derivedIncludedCount());

        for (final GeneratedCatalogEntry entry : result.entries()) {
            final String path = "entries." + entry.key();
            yaml.set(path + ".category", entry.category().name());
            yaml.set(path + ".policy", entry.policy().name());
            yaml.set(path + ".root-value-present", entry.rootValuePresent());
            yaml.set(path + ".root-value", entry.rootValue() != null ? entry.rootValue().toPlainString() : null);
            yaml.set(path + ".derivation-depth", entry.derivationDepth());
            yaml.set(path + ".derived-value", entry.derivedValue() != null ? entry.derivedValue().toPlainString() : null);
            yaml.set(path + ".derivation-reason", entry.derivationReason());
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
        yaml.set("root-anchored-count", result.rootAnchoredCount());
        yaml.set("derived-included-count", result.derivedIncludedCount());

        yaml.save(summaryFile);
        return summaryFile;
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
            + ", rootAnchored=" + result.rootAnchoredCount()
            + ", derivedIncluded=" + result.derivedIncludedCount();
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/generate/CatalogGeneratorFacade.java`

```java
package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.classify.DefaultCategoryClassifier;
import com.splatage.wild_economy.catalog.derive.RootAnchoredDerivationService;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.policy.DefaultPolicySuggestionService;
import com.splatage.wild_economy.catalog.recipe.BukkitRecipeGraphBuilder;
import com.splatage.wild_economy.catalog.recipe.RecipeGraph;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import java.io.File;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

public final class CatalogGeneratorFacade {

    private static final int DEFAULT_MAX_DERIVATION_DEPTH = 1;

    private final JavaPlugin plugin;

    public CatalogGeneratorFacade(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CatalogGenerationResult generateFromRootValuesFile(final File rootValuesFile) throws IOException {
        final RootValueLoader rootValueLoader = RootValueLoader.fromFile(rootValuesFile);
        final RecipeGraph recipeGraph = new BukkitRecipeGraphBuilder().build();
        final RootAnchoredDerivationService derivationService = new RootAnchoredDerivationService(
            recipeGraph,
            rootValueLoader,
            DEFAULT_MAX_DERIVATION_DEPTH
        );

        final CatalogGenerationService service = new CatalogGenerationService(
            new BukkitMaterialScanner(rootValueLoader),
            new DefaultCategoryClassifier(),
            new DefaultPolicySuggestionService(),
            derivationService
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

## File: `src/main/java/com/splatage/wild_economy/catalog/README_PHASE2_NOTES.md`

```md
# Phase 2 Catalog Generator Notes

This phase adds rooted recipe derivation.

## Locked rules

- `root-values.yml` is the only anchor source.
- Only anchored items can seed derivation.
- Missing anchors block that derivation path.
- An item may still be included if another complete recipe path resolves from anchored roots.
- The chosen path is the minimum valid derivation depth.
- Depth is measured in craft steps from anchored roots.
- The current default max derivation depth in the facade is `1`.

## Expected behavior example

- `jungle_log` with root value -> included at depth `0`
- `jungle_planks` crafted from `jungle_log` -> included at depth `1`
- `jungle_stairs` crafted from `jungle_planks` -> blocked by default depth limit `1`

## Current simplifications

- value derivation is ingredient total divided by output count
- no crafting tax / bonus yet
- recipe choices only support `MaterialChoice`
- exact-meta recipes are skipped
- very large combinational choice explosions are skipped

## Intended later phases

- config-driven derivation depth instead of facade constant
- generated base catalog as runtime source
- `exchange-items.yml` overrides winning last
- more specific policy heuristics beyond root/derived inclusion
```
