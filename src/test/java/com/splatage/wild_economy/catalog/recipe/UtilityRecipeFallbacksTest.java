package com.splatage.wild_economy.catalog.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.catalog.derive.RootAnchoredDerivationService;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class UtilityRecipeFallbacksTest {

    @Test
    void apply_unblocksCompressionContainerFoodAndSmokerFamilies() {
        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();
        recipesByOutput.put(
            "furnace",
            List.of(new RecipeDefinition(
                "furnace",
                1,
                "test_seed_furnace",
                List.of(new RecipeIngredient("cobblestone", 8))
            ))
        );
        recipesByOutput.put(
            "bone_meal",
            List.of(new RecipeDefinition(
                "bone_meal",
                3,
                "test_seed_bone_meal",
                List.of(new RecipeIngredient("bone", 1))
            ))
        );

        RecipeGraphFallbacks.apply(recipesByOutput);

        final RootAnchoredDerivationService service = new RootAnchoredDerivationService(
            new RecipeGraph(copyOf(recipesByOutput)),
            rootValues(Map.of(
                "oak_log", new BigDecimal("12.00"),
                "cobblestone", new BigDecimal("2.00"),
                "stick", new BigDecimal("4.00"),
                "coal", new BigDecimal("6.00"),
                "beetroot", new BigDecimal("3.00"),
                "bone", new BigDecimal("3.00")
            ))
        );

        assertTrue(service.resolve("bone_block").resolved());
        assertEquals(new BigDecimal("9"), service.resolve("bone_block").derivedValue().stripTrailingZeros());

        assertTrue(service.resolve("bowl").resolved());
        assertEquals(new BigDecimal("2.25"), service.resolve("bowl").derivedValue().stripTrailingZeros());

        assertTrue(service.resolve("beetroot_soup").resolved());
        assertEquals(new BigDecimal("20.25"), service.resolve("beetroot_soup").derivedValue().stripTrailingZeros());

        assertTrue(service.resolve("campfire").resolved());
        assertEquals(new BigDecimal("54"), service.resolve("campfire").derivedValue().stripTrailingZeros());

        assertTrue(service.resolve("smoker").resolved());
        assertEquals(new BigDecimal("64"), service.resolve("smoker").derivedValue().stripTrailingZeros());
    }

    private static RootValueLookup rootValues(final Map<String, BigDecimal> values) {
        return itemKey -> Optional.ofNullable(values.get(itemKey));
    }

    private static Map<String, List<RecipeDefinition>> copyOf(final Map<String, List<RecipeDefinition>> input) {
        final Map<String, List<RecipeDefinition>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, List<RecipeDefinition>> entry : input.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }
}
