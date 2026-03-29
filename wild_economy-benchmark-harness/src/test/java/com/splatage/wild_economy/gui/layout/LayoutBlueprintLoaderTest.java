package com.splatage.wild_economy.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LayoutBlueprintLoaderTest {

    @Test
    void load_parsesGroupsChildrenPatternsAndOverrides() throws Exception {
        final Path tempFile = Files.createTempFile("layout", ".yml");
        Files.writeString(tempFile, """
            layout:
              groups:
                MISC:
                  label: "Misc"
                  order: 7
                  children:
                    OTHER:
                      label: "Other"
                      item-key-patterns:
                        - "minecraft:music_disc_*"
              overrides:
                minecraft:dragon_egg:
                  group: MISC
                  child: OTHER
            """);

        final LayoutBlueprint blueprint = new LayoutBlueprintLoader().load(tempFile.toFile());
        final LayoutGroupDefinition misc = blueprint.groups().get("MISC");
        assertEquals("Misc", misc.label());
        assertEquals(7, misc.order());
        assertTrue(misc.children().containsKey("OTHER"));
        assertEquals(List.of("music_disc_*"), misc.children().get("OTHER").itemKeyPatterns());
        assertEquals("MISC", blueprint.overrides().get("dragon_egg").group());
        assertEquals("OTHER", blueprint.overrides().get("dragon_egg").child());
    }
}
