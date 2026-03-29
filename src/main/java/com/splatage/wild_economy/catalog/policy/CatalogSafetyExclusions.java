package com.splatage.wild_economy.catalog.policy;

import java.util.Set;

public final class CatalogSafetyExclusions {

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
        "written_book",
        "spawner",
        "trial_spawner",
        "vault"
    );

    private CatalogSafetyExclusions() {
    }

    public static boolean isHardDisabled(final String key) {
        return HARD_DISABLED_EXACT.contains(key) || key.endsWith("_spawn_egg");
    }
}
