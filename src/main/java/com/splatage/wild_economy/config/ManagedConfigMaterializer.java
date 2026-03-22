package com.splatage.wild_economy.config;

import com.splatage.wild_economy.WildEconomyPlugin;
import java.io.File;
import java.util.List;
import java.util.Objects;

public final class ManagedConfigMaterializer {

    private static final List<ManagedConfigFile> MANAGED_FILES = List.of(
        new ManagedConfigFile(
            "config.yml",
            "Review general settings in config.yml, then run /shopadmin reload after any edits."
        ),
        new ManagedConfigFile(
            "database.yml",
            "Review database settings before production use, then run /shopadmin reload after any edits."
        ),
        new ManagedConfigFile(
            "exchange-items.yml",
            "Review live catalog overrides, then run /shopadmin reload after any edits."
        ),
        new ManagedConfigFile(
            "root-values.yml",
            "Review root values, then run /shopadmin catalog validate before applying generated catalog changes."
        ),
        new ManagedConfigFile(
            "messages.yml",
            "Review custom messages, then run /shopadmin reload after any edits."
        ),
        new ManagedConfigFile(
            "policy-rules.yml",
            "Review catalog policy rules, then run /shopadmin catalog validate before /shopadmin catalog apply."
        ),
        new ManagedConfigFile(
            "policy-profiles.yml",
            "Review policy behavior profiles, then run /shopadmin catalog validate before /shopadmin catalog apply."
        ),
        new ManagedConfigFile(
            "manual-overrides.yml",
            "Review manual overrides, then run /shopadmin catalog validate before /shopadmin catalog apply."
        ),
        new ManagedConfigFile(
            "stock-profiles.yml",
            "Review stock profile defaults, then run /shopadmin catalog validate before /shopadmin catalog apply."
        ),
        new ManagedConfigFile(
            "eco-envelopes.yml",
            "Review eco envelope defaults, then run /shopadmin catalog validate before /shopadmin catalog apply."
        )
    );

    private final WildEconomyPlugin plugin;

    public ManagedConfigMaterializer(final WildEconomyPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void ensureManagedFiles() {
        final File dataFolder = this.plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        for (final ManagedConfigFile managedFile : MANAGED_FILES) {
            this.ensureManagedFile(managedFile);
        }
    }

    private void ensureManagedFile(final ManagedConfigFile managedFile) {
        final File targetFile = new File(this.plugin.getDataFolder(), managedFile.resourcePath());
        if (targetFile.isFile()) {
            return;
        }
        if (targetFile.exists()) {
            throw new IllegalStateException(
                "Managed config path exists but is not a file: " + targetFile.getAbsolutePath()
            );
        }

        try {
            this.plugin.saveResource(managedFile.resourcePath(), false);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Bundled managed config resource '" + managedFile.resourcePath() + "' is missing from the plugin jar.",
                exception
            );
        }

        this.plugin.getLogger().warning(
            "Managed config file '" + managedFile.resourcePath() + "' was missing and has been regenerated from bundled defaults at "
                + targetFile.getAbsolutePath()
                + "."
        );
        this.plugin.getLogger().warning("Next step: " + managedFile.nextStep());
    }

    private record ManagedConfigFile(String resourcePath, String nextStep) {
    }
}
