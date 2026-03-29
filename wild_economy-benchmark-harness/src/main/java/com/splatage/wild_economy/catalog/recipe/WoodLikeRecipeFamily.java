package com.splatage.wild_economy.catalog.recipe;

record WoodLikeRecipeFamily(
    String familyKey,
    String inputUnitKey,
    String strippedInputUnitKey,
    String planksKey,
    int planksOutputAmount,
    String woodLikeOutputKey,
    int woodLikeOutputAmount,
    String slabKey,
    String signKey,
    String hangingSignKey,
    String stairsKey,
    String boatLikeKey,
    String chestBoatLikeKey,
    boolean supportsBoatRecipes
) {
}
