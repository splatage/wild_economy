package com.splatage.wild_economy.store.model;

import java.util.List;

public interface StoreAccessControlled {
    StoreVisibilityWhenUnmet visibilityWhenUnmet();
    String lockedMessage();
    List<StoreRequirement> requirements();
}
