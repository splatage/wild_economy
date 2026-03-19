package com.splatage.wild_economy.catalog.scan;

import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.List;

public interface MaterialScanner {
    List<ItemFacts> scanAll();
}
