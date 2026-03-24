package com.splatage.wild_economy.catalog.recipe;

import java.util.List;

final class WoodLikeRecipeFamilies {

    private static final List<WoodLikeRecipeFamily> FAMILIES = List.of(
        normal("oak"),
        normal("spruce"),
        normal("birch"),
        normal("jungle"),
        normal("acacia"),
        normal("dark_oak"),
        normal("mangrove"),
        normal("cherry"),
        normal("pale_oak"),
        nether("crimson"),
        nether("warped"),
        bamboo()
    );

    private WoodLikeRecipeFamilies() {
    }

    static List<WoodLikeRecipeFamily> all() {
        return FAMILIES;
    }

    private static WoodLikeRecipeFamily normal(final String familyKey) {
        return new WoodLikeRecipeFamily(
            familyKey,
            familyKey + "_log",
            "stripped_" + familyKey + "_log",
            familyKey + "_planks",
            4,
            familyKey + "_wood",
            3,
            familyKey + "_slab",
            familyKey + "_sign",
            familyKey + "_hanging_sign",
            familyKey + "_stairs",
            familyKey + "_boat",
            familyKey + "_chest_boat",
            true
        );
    }

    private static WoodLikeRecipeFamily nether(final String familyKey) {
        return new WoodLikeRecipeFamily(
            familyKey,
            familyKey + "_stem",
            "stripped_" + familyKey + "_stem",
            familyKey + "_planks",
            4,
            familyKey + "_hyphae",
            3,
            familyKey + "_slab",
            familyKey + "_sign",
            familyKey + "_hanging_sign",
            familyKey + "_stairs",
            null,
            null,
            false
        );
    }

    private static WoodLikeRecipeFamily bamboo() {
        return new WoodLikeRecipeFamily(
            "bamboo",
            "bamboo_block",
            "stripped_bamboo_block",
            "bamboo_planks",
            2,
            null,
            0,
            "bamboo_slab",
            "bamboo_sign",
            "bamboo_hanging_sign",
            "bamboo_stairs",
            "bamboo_raft",
            "bamboo_chest_raft",
            true
        );
    }
}
