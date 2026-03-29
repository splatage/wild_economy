package com.splatage.wild_economy.testing.scenario;

import java.util.Random;

public record ScenarioMix(
        int browseHeavyWeight,
        int buyHeavyWeight,
        int sellHeavyWeight,
        int mixedEconomyWeight
) {
    public ScenarioMix {
        if (browseHeavyWeight < 0 || buyHeavyWeight < 0 || sellHeavyWeight < 0 || mixedEconomyWeight < 0) {
            throw new IllegalArgumentException("Scenario mix weights cannot be negative");
        }
        if ((long) browseHeavyWeight + buyHeavyWeight + sellHeavyWeight + mixedEconomyWeight <= 0L) {
            throw new IllegalArgumentException("Scenario mix must contain at least one positive weight");
        }
    }

    public int selectIndex(final Random random) {
        final int totalWeight = this.browseHeavyWeight + this.buyHeavyWeight + this.sellHeavyWeight + this.mixedEconomyWeight;
        int roll = random.nextInt(totalWeight);
        if (roll < this.browseHeavyWeight) {
            return 0;
        }
        roll -= this.browseHeavyWeight;
        if (roll < this.buyHeavyWeight) {
            return 1;
        }
        roll -= this.buyHeavyWeight;
        if (roll < this.sellHeavyWeight) {
            return 2;
        }
        return 3;
    }
}
