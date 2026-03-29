package com.splatage.wild_economy.catalog.recipe;

import com.splatage.wild_economy.catalog.derive.RootAnchoredDerivationService;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class RecipeGraphFallbacksTest {

    @Test
    void apply_addsChestFallbackRecipesAcrossWoodLikeFamilies() {
        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();

        RecipeGraphFallbacks.apply(recipesByOutput);

        final List<RecipeDefinition> chestRecipes = recipesByOutput.get("chest");
        assertNotNull(chestRecipes);
        assertFalse(chestRecipes.isEmpty());
        assertTrue(
            chestRecipes.stream().anyMatch(recipe -> recipe.ingredients().equals(List.of(new RecipeIngredient("oak_planks", 8)))),
            "expected at least one oak-plank chest fallback"
        );
    }

    @Test
    void apply_addsBambooFamilyFallbacksWhenMaterialsExist() {
        assumeTrue(Material.matchMaterial("BAMBOO_SIGN") != null);
        assumeTrue(Material.matchMaterial("BAMBOO_HANGING_SIGN") != null);
        assumeTrue(Material.matchMaterial("BAMBOO_RAFT") != null);
        assumeTrue(Material.matchMaterial("BAMBOO_CHEST_RAFT") != null);

        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();

        RecipeGraphFallbacks.apply(recipesByOutput);

        assertTrue(recipesByOutput.containsKey("bamboo_sign"));
        assertTrue(recipesByOutput.containsKey("bamboo_hanging_sign"));
        assertTrue(recipesByOutput.containsKey("bamboo_raft"));
        assertTrue(recipesByOutput.containsKey("bamboo_chest_raft"));
    }

    @Test
    void apply_unblocksCommonUtilityChainsFromRootAnchors() {
        assumeTrue(Material.matchMaterial("CRAFTER") != null);

        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();
        recipesByOutput.put(
            "crafting_table",
            List.of(new RecipeDefinition(
                "crafting_table",
                1,
                "test_seed_crafting_table_from_planks",
                List.of(new RecipeIngredient("oak_planks", 4))
            ))
        );

        RecipeGraphFallbacks.apply(recipesByOutput);

        final RootAnchoredDerivationService service = new RootAnchoredDerivationService(
            new RecipeGraph(copyOf(recipesByOutput)),
            rootValues(Map.of(
                "oak_log", new BigDecimal("12.00"),
                "cobblestone", new BigDecimal("2.00"),
                "stick", new BigDecimal("4.00"),
                "string", new BigDecimal("4.00"),
                "redstone", new BigDecimal("6.00"),
                "iron_ingot", new BigDecimal("10.00"),
                "smooth_stone", new BigDecimal("2.50")
            ))
        );

        assertTrue(service.resolve("chest").resolved());
        assertTrue(service.resolve("dropper").resolved());
        assertTrue(service.resolve("hopper").resolved());
        assertTrue(service.resolve("blast_furnace").resolved());
        assertTrue(service.resolve("crafter").resolved());
        assertEquals(new BigDecimal("94"), service.resolve("crafter").derivedValue().stripTrailingZeros());
    }

    private static RootValueLookup rootValues(final Map<String, BigDecimal> values) {
        return itemKey -> Optional.ofNullable(values.get(itemKey));
    }

    private static Map<String, List<RecipeDefinition>> copyOf(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        final Map<String, List<RecipeDefinition>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, List<RecipeDefinition>> entry : recipesByOutput.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}
