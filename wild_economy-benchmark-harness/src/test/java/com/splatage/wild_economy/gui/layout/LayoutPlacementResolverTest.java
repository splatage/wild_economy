package com.splatage.wild_economy.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.splatage.wild_economy.exchange.domain.ItemKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LayoutPlacementResolverTest {

    @Test
    void resolve_usesChildPatternsBeforeFallback() {
        final LayoutBlueprint blueprint = new LayoutBlueprint(
            Map.of(
                "COMBAT_ADVENTURE",
                new LayoutGroupDefinition(
                    "COMBAT_ADVENTURE",
                    "Combat / Adventure",
                    0,
                    null,
                    Map.of(
                        "WEAPONS",
                        new LayoutChildDefinition("WEAPONS", "Weapons", 0, null, List.of(), List.of("*_sword")),
                        "TOOLS",
                        new LayoutChildDefinition("TOOLS", "Tools", 1, null, List.of(), List.of("*_pickaxe"))
                    ),
                    List.of(),
                    List.of()
                ),
                "MISC",
                new LayoutGroupDefinition(
                    "MISC",
                    "Misc",
                    1,
                    null,
                    Map.of("OTHER", new LayoutChildDefinition("OTHER", "Other", 0, null, List.of(), List.of())),
                    List.of(),
                    List.of()
                )
            ),
            Map.of()
        );

        final LayoutPlacementResolver resolver = new LayoutPlacementResolver(blueprint);
        final LayoutPlacement sword = resolver.resolve(new ItemKey("minecraft:diamond_sword"));
        final LayoutPlacement unknown = resolver.resolve(new ItemKey("minecraft:weird_custom_thing"));

        assertEquals("COMBAT_ADVENTURE", sword.groupKey());
        assertEquals("WEAPONS", sword.childKey());
        assertEquals("MISC", unknown.groupKey());
        assertEquals("OTHER", unknown.childKey());
    }

    @Test
    void resolve_override_wins() {
        final Map<String, LayoutGroupDefinition> groups = new LinkedHashMap<>();
        groups.put(
            "MISC",
            new LayoutGroupDefinition(
                "MISC",
                "Misc",
                0,
                null,
                Map.of("OTHER", new LayoutChildDefinition("OTHER", "Other", 0, null, List.of(), List.of())),
                List.of(),
                List.of()
            )
        );
        groups.put(
            "REDSTONE_UTILITIES",
            new LayoutGroupDefinition(
                "REDSTONE_UTILITIES",
                "Redstone / Utilities",
                1,
                null,
                Map.of("TRANSPORT", new LayoutChildDefinition("TRANSPORT", "Transport", 0, null, List.of(), List.of())),
                List.of(),
                List.of()
            )
        );

        final LayoutBlueprint blueprint = new LayoutBlueprint(
            groups,
            Map.of("oak_boat", new LayoutOverride("oak_boat", "REDSTONE_UTILITIES", "TRANSPORT"))
        );

        final LayoutPlacementResolver resolver = new LayoutPlacementResolver(blueprint);
        final LayoutPlacement placement = resolver.resolve(new ItemKey("minecraft:oak_boat"));

        assertEquals("REDSTONE_UTILITIES", placement.groupKey());
        assertEquals("TRANSPORT", placement.childKey());
    }
}
