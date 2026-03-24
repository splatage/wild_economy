package com.splatage.wild_economy.catalog.rootvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RootValueLoaderTest {

    @Test
    void fromFile_loadsRootValuesAndLayoutGroupHints() throws Exception {
        final Path file = Files.createTempFile("root-values", ".yml");
        Files.writeString(
            file,
            """
            items:
              minecraft:redstone: 4.00
              minecraft:bone: 3.00

            layout:
              groups:
                redstone:
                  generated-category: REDSTONE
                  item-keys:
                    - \"minecraft:redstone\"
                decoration:
                  generated-category: DECORATION
                  item-key-patterns:
                    - \"minecraft:*_coral_block\"
            """
        );

        final RootValueLoader loader = RootValueLoader.fromFile(file.toFile());

        assertEquals(new BigDecimal("4.0"), loader.findRootValue("minecraft:redstone").orElseThrow());
        assertEquals(CatalogCategory.REDSTONE, loader.findCategoryHint("minecraft:redstone").orElseThrow());
        assertEquals(CatalogCategory.DECORATION, loader.findCategoryHint("tube_coral_block").orElseThrow());
        assertTrue(loader.findCategoryHint("minecraft:bone").isEmpty());
    }
}
