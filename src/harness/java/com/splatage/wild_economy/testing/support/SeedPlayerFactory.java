package com.splatage.wild_economy.testing.support;

import com.splatage.wild_economy.economy.model.MoneyAmount;
import com.splatage.wild_economy.testing.seed.SeedPlan;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public final class SeedPlayerFactory {

    public List<SeedPlayer> createPlayers(final SeedPlan seedPlan, final int fractionalDigits) {
        final Random random = new Random(seedPlan.randomSeed());
        final List<SeedPlayer> players = new ArrayList<>(seedPlan.playerCount());
        for (int index = 0; index < seedPlan.playerCount(); index++) {
            final String playerName = "%s_player_%05d".formatted(seedPlan.profile().name().toLowerCase(Locale.ROOT), index + 1);
            final UUID playerId = UUID.nameUUIDFromBytes((playerName + ':' + seedPlan.randomSeed()).getBytes(StandardCharsets.UTF_8));
            final int balanceRoll = random.nextInt(100);
            final long balanceMajor;
            if (balanceRoll < 40) {
                balanceMajor = 25L + random.nextInt(250);
            } else if (balanceRoll < 75) {
                balanceMajor = 250L + random.nextInt(2_500);
            } else if (balanceRoll < 92) {
                balanceMajor = 2_500L + random.nextInt(10_000);
            } else {
                balanceMajor = 10_000L + random.nextInt(50_000);
            }
            players.add(new SeedPlayer(
                    playerId,
                    playerName,
                    MoneyAmount.ofMinor(balanceMajor * pow10(fractionalDigits))
            ));
        }
        return List.copyOf(players);
    }

    private static long pow10(final int exponent) {
        long value = 1L;
        for (int index = 0; index < exponent; index++) {
            value *= 10L;
        }
        return value;
    }
}
