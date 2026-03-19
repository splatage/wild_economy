package com.splatage.wild_economy.gui;

public final class MenuSession {
}
package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.exchange.domain.ItemCategory;
import java.util.UUID;

public record MenuSession(
    UUID playerId,
    ItemCategory currentCategory,
    int currentPage
) {}
