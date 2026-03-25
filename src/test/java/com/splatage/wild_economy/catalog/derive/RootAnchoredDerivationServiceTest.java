package com.splatage.wild_economy.catalog.derive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.catalog.recipe.RecipeDefinition;
import com.splatage.wild_economy.catalog.recipe.RecipeGraph;
import com.splatage.wild_economy.catalog.recipe.RecipeIngredient;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class RootAnchoredDerivationServiceTest {

    @Test
    void resolve_stabilizesCraftedIntermediatesAcrossMultiplePasses() {
        final Map<String, List<RecipeDefinition>> recipesByOutput = new LinkedHashMap<>();
        recipesByOutput.put(
            "planks",
            List.of(new RecipeDefinition(
                "planks",
                4,
                "test_planks",
                List.of(new RecipeIngredient("log", 1))
            ))
        );
        recipesByOutput.put(
            "stick",
            List.of(new RecipeDefinition(
                "stick",
                4,
                "test_stick",
                List.of(new RecipeIngredient("planks", 2))
            ))
        );
        recipesByOutput.put(
            "torch",
            List.of(new RecipeDefinition(
                "torch",
                4,
                "test_torch",
                List.of(
                    new RecipeIngredient("stick", 1),
                    new RecipeIngredient("coal", 1)
                )
            ))
        );

        final RootAnchoredDerivationService service = new RootAnchoredDerivationService(
            new RecipeGraph(recipesByOutput),
            rootValues(Map.of(
                "log", new BigDecimal("12.00"),
                "coal", new BigDecimal("6.00")
            ))
        );

        assertTrue(service.resolve("planks").resolved());
        assertEquals(new BigDecimal("3"), service.resolve("planks").derivedValue().stripTrailingZeros());

        assertTrue(service.resolve("stick").resolved());
        assertEquals(new BigDecimal("1.5"), service.resolve("stick").derivedValue().stripTrailingZeros());

        assertTrue(service.resolve("torch").resolved());
        assertEquals(new BigDecimal("1.875"), service.resolve("torch").derivedValue().stripTrailingZeros());
    }

    private static RootValueLookup rootValues(final Map<String, BigDecimal> values) {
        return itemKey -> Optional.ofNullable(values.get(itemKey));
    }
}
