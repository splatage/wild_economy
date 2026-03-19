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
    private final Map<String, DerivedItemResult> cache = new HashMap<>();
    private final Set<String> visiting = new HashSet<>();

    public RootAnchoredDerivationService(
        final RecipeGraph recipeGraph,
        final RootValueLookup rootValueLookup
    ) {
        this.recipeGraph = recipeGraph;
        this.rootValueLookup = rootValueLookup;
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

            final DerivedItemResult result = DerivedItemResult.derived(best.depth(), best.value());
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
            if (!ingredientResult.resolved() || ingredientResult.derivedValue() == null || ingredientResult.derivationDepth() == null) {
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
