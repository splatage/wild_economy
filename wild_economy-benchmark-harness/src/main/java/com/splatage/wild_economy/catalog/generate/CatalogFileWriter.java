package com.splatage.wild_economy.catalog.generate;

import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import com.splatage.wild_economy.catalog.model.GeneratedCatalogEntry;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CatalogFileWriter {

    private final JavaPlugin plugin;

    public CatalogFileWriter(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File writeGeneratedCatalog(final CatalogGenerationResult result) throws IOException {
        final File generatedDir = new File(this.plugin.getDataFolder(), "generated");
        if (!generatedDir.exists() && !generatedDir.mkdirs()) {
            throw new IOException("Failed to create generated directory: " + generatedDir.getAbsolutePath());
        }

        final File catalogFile = new File(generatedDir, "generated-catalog.yml");
        final YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("generated-at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        yaml.set("summary.total-scanned", result.totalScanned());
        yaml.set("summary.total-generated", result.totalGenerated());
        yaml.set("summary.total-disabled", result.totalDisabled());
        yaml.set("summary.root-anchored-count", result.rootAnchoredCount());
        yaml.set("summary.derived-included-count", result.derivedIncludedCount());

        for (final GeneratedCatalogEntry entry : result.entries()) {
            final String path = "entries." + entry.key();
            yaml.set(path + ".category", entry.category().name());
            yaml.set(path + ".policy", entry.policy().name());
            yaml.set(path + ".root-value-present", entry.rootValuePresent());
            yaml.set(path + ".root-value", entry.rootValue() != null ? entry.rootValue().toPlainString() : null);
            yaml.set(path + ".derivation-depth", entry.derivationDepth());
            yaml.set(path + ".derived-value", entry.derivedValue() != null ? entry.derivedValue().toPlainString() : null);
            yaml.set(path + ".derivation-reason", entry.derivationReason());
            yaml.set(path + ".include-reason", entry.includeReason());
            yaml.set(path + ".exclude-reason", entry.excludeReason());
            yaml.set(path + ".notes", entry.notes());
        }

        yaml.save(catalogFile);
        return catalogFile;
    }

    public File writeSummary(final CatalogGenerationResult result) throws IOException {
        final File generatedDir = new File(this.plugin.getDataFolder(), "generated");
        if (!generatedDir.exists() && !generatedDir.mkdirs()) {
            throw new IOException("Failed to create generated directory: " + generatedDir.getAbsolutePath());
        }

        final File summaryFile = new File(generatedDir, "generated-catalog-summary.yml");
        final YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("generated-at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        yaml.set("total-scanned", result.totalScanned());
        yaml.set("total-generated", result.totalGenerated());
        yaml.set("total-disabled", result.totalDisabled());
        yaml.set("root-anchored-count", result.rootAnchoredCount());
        yaml.set("derived-included-count", result.derivedIncludedCount());

        yaml.save(summaryFile);
        return summaryFile;
    }
}
