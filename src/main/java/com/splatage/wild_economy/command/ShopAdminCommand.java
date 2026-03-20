package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.generate.CatalogGenerationReportFormatter;
import com.splatage.wild_economy.catalog.generate.CatalogGeneratorFacade;
import com.splatage.wild_economy.catalog.model.CatalogGenerationResult;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ShopAdminCommand implements CommandExecutor {

    private final WildEconomyPlugin plugin;

    public ShopAdminCommand(final WildEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Use /shopadmin <reload|generatecatalog>");
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> this.handleReload(sender);
            case "generatecatalog" -> this.handleGenerateCatalog(sender);
            default -> {
                sender.sendMessage("Unknown admin subcommand. Use /shopadmin <reload|generatecatalog>");
                yield true;
            }
        };
    }

    private boolean handleReload(final CommandSender sender) {
        try {
            this.plugin.reloadConfig();
            this.plugin.getBootstrap().reload();
            sender.sendMessage("wild_economy reloaded.");
        } catch (final RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reload wild_economy", exception);
            sender.sendMessage("Reload failed. Check console for details.");
        }
        return true;
    }

    private boolean handleGenerateCatalog(final CommandSender sender) {
        final File rootValuesFile = new File(this.plugin.getDataFolder(), "root-values.yml");
        if (!rootValuesFile.exists() || !rootValuesFile.isFile()) {
            sender.sendMessage("Catalog generation aborted: root-values.yml not found at " + rootValuesFile.getPath());
            return true;
        }

        try {
            final CatalogGeneratorFacade facade = new CatalogGeneratorFacade(this.plugin);
            final CatalogGenerationResult result = facade.generateFromRootValuesFile(rootValuesFile);
            facade.writeOutputs(result);

            final File generatedDir = new File(this.plugin.getDataFolder(), "generated");
            sender.sendMessage("Generated catalog files in " + generatedDir.getPath());
            sender.sendMessage(CatalogGenerationReportFormatter.formatSingleLine(result));
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to generate catalog data", exception);
            sender.sendMessage("Failed to generate catalog data: " + exception.getMessage());
            return true;
        }
    }
}
