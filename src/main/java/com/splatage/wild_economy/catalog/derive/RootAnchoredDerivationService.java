package com.splatage.wild_economy.catalog.derive;

import com.splatage.wild_economy.catalog.recipe.RecipeDefinition;
import com.splatage.wild_economy.catalog.recipe.RecipeGraph;
import com.splatage.wild_economy.catalog.recipe.RecipeIngredient;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RootAnchoredDerivationService {

    private final RecipeGraph recipeGraph;
    private final RootValueLookup rootValueLookup;
    private final Map<String, DerivedItemResult> cache = new HashMap<>();
    private boolean stabilized;

    public RootAnchoredDerivationService(
        final RecipeGraph recipeGraph,
        final RootValueLookup rootValueLookup
    ) {
        this.recipeGraph = recipeGraph;
        this.rootValueLookup = rootValueLookup;
    }

    public DerivedItemResult resolve(final String itemKey) {
        this.stabilizeUntilStable();

        final DerivedItemResult cached = this.cache.get(itemKey);
        if (cached != null) {
            return cached;
        }

        final BigDecimal rootValue = this.rootValueLookup.findRootValue(itemKey).orElse(null);
        if (rootValue != null) {
            final DerivedItemResult result = DerivedItemResult.rootAnchor(rootValue);
            this.cache.put(itemKey, result);
            return result;
        }

        if (this.recipeGraph.getRecipesFor(itemKey).isEmpty()) {
            return DerivedItemResult.blocked(null, null, DerivationReason.NO_RECIPE_AND_NO_ROOT);
        }

        return DerivedItemResult.blocked(null, null, DerivationReason.ALL_PATHS_BLOCKED);
    }

    private void stabilizeUntilStable() {
        if (this.stabilized) {
            return;
        }

        for (final String itemKey : this.recipeGraph.allOutputKeys()) {
            final BigDecimal rootValue = this.rootValueLookup.findRootValue(itemKey).orElse(null);
            if (rootValue != null) {
                this.cache.put(itemKey, DerivedItemResult.rootAnchor(rootValue));
            }
        }

        boolean changed;
        do {
            changed = false;
            for (final String itemKey : this.recipeGraph.allOutputKeys()) {
                final BigDecimal rootValue = this.rootValueLookup.findRootValue(itemKey).orElse(null);
                if (rootValue != null) {
                    continue;
                }

                final CandidatePath best = this.calculateBestKnownPath(itemKey);
                if (best == null) {
                    continue;
                }

                final DerivedItemResult existing = this.cache.get(itemKey);
                final DerivedItemResult candidate = DerivedItemResult.derived(best.depth(), best.value());
                if (!candidate.equals(existing)) {
                    this.cache.put(itemKey, candidate);
                    changed = true;
                }
            }
        } while (changed);

        this.stabilized = true;
    }

    private CandidatePath calculateBestKnownPath(final String itemKey) {
        final List<RecipeDefinition> recipes = this.recipeGraph.getRecipesFor(itemKey);
        if (recipes.isEmpty()) {
            return null;
        }

        CandidatePath best = null;
        for (final RecipeDefinition recipe : recipes) {
            final CandidatePath candidate = this.evaluateRecipeAgainstKnownValues(recipe);
            if (candidate == null) {
                continue;
            }
            if (best == null
                || Comparator.comparingInt(CandidatePath::depth).thenComparing(CandidatePath::value).compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private CandidatePath evaluateRecipeAgainstKnownValues(final RecipeDefinition recipe) {
        BigDecimal totalIngredientValue = BigDecimal.ZERO;
        int maxIngredientDepth = 0;

        for (final RecipeIngredient ingredient : recipe.ingredients()) {
            final DerivedItemResult ingredientResult = this.knownResult(ingredient.itemKey());
            if (ingredientResult == null
                || !ingredientResult.resolved()
                || ingredientResult.derivedValue() == null
                || ingredientResult.derivationDepth() == null) {
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

    private DerivedItemResult knownResult(final String itemKey) {
        final DerivedItemResult cached = this.cache.get(itemKey);
        if (cached != null) {
            return cached;
        }

        final BigDecimal rootValue = this.rootValueLookup.findRootValue(itemKey).orElse(null);
        if (rootValue == null) {
            return null;
        }

        final DerivedItemResult result = DerivedItemResult.rootAnchor(rootValue);
        this.cache.put(itemKey, result);
        return result;
    }

    private record CandidatePath(int depth, BigDecimal value) {
    }
}
