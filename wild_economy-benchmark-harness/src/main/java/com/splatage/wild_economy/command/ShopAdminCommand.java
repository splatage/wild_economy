package com.splatage.wild_economy.command;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogActionService;
import com.splatage.wild_economy.gui.admin.AdminMenuRouter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopAdminCommand implements CommandExecutor {

    private static final String PERMISSION_VIEW = "wild_economy.admin.view";
    private static final String PERMISSION_RELOAD = "wild_economy.admin.reload";
    private static final String PERMISSION_APPLY = "wild_economy.admin.apply";

    private final WildEconomyPlugin plugin;
    private final AdminCatalogActionService actionService;
    private final AdminCatalogCommandSummaryFormatter summaryFormatter;
    private final AdminMenuRouter adminMenuRouter;

    public ShopAdminCommand(final WildEconomyPlugin plugin, final AdminMenuRouter adminMenuRouter) {
        this.plugin = plugin;
        this.actionService = new AdminCatalogActionService(plugin);
        this.summaryFormatter = new AdminCatalogCommandSummaryFormatter();
        this.adminMenuRouter = adminMenuRouter;
    }

    @Override
    public boolean onCommand(
        final CommandSender sender,
        final Command command,
        final String label,
        final String[] args
    ) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_VIEW,
                    ChatColor.RED + "You do not have permission to view wild_economy admin screens."
                )) {
                    return true;
                }
                this.adminMenuRouter.openRoot(player);
            } else {
                this.sendUsage(sender);
            }
            return true;
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_RELOAD,
                    ChatColor.RED + "You do not have permission to reload wild_economy."
                )) {
                    yield true;
                }
                yield this.handleReload(sender);
            }
            case "gui" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_VIEW,
                    ChatColor.RED + "You do not have permission to view wild_economy admin screens."
                )) {
                    yield true;
                }
                yield this.handleGui(sender);
            }
            case "generatecatalog" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_VIEW,
                    ChatColor.RED + "You do not have permission to preview the generated catalog."
                )) {
                    yield true;
                }
                yield this.handleCatalogPreview(sender);
            }
            case "catalog" -> this.handleCatalog(sender, args);
            case "item" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_VIEW,
                    ChatColor.RED + "You do not have permission to inspect generated catalog items."
                )) {
                    yield true;
                }
                yield this.handleItemInspect(sender, args);
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown admin subcommand.");
                this.sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleCatalog(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>.");
            return true;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "preview" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_VIEW,
                    ChatColor.RED + "You do not have permission to preview the generated catalog."
                )) {
                    yield true;
                }
                yield this.handleCatalogPreview(sender);
            }
            case "validate" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_VIEW,
                    ChatColor.RED + "You do not have permission to validate the generated catalog."
                )) {
                    yield true;
                }
                yield this.handleCatalogValidate(sender);
            }
            case "diff" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_VIEW,
                    ChatColor.RED + "You do not have permission to diff the generated catalog."
                )) {
                    yield true;
                }
                yield this.handleCatalogDiff(sender);
            }
            case "apply" -> {
                if (!this.ensurePermission(
                    sender,
                    PERMISSION_APPLY,
                    ChatColor.RED + "You do not have permission to publish the generated live catalog."
                )) {
                    yield true;
                }
                yield this.handleCatalogApply(sender);
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown catalog action.");
                sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>.");
                yield true;
            }
        };
    }

    private boolean handleGui(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the admin review GUI.");
            return true;
        }

        if (!this.ensurePermission(
            sender,
            PERMISSION_VIEW,
            ChatColor.RED + "You do not have permission to view wild_economy admin screens."
        )) {
            return true;
        }

        this.adminMenuRouter.openRoot(player);
        return true;
    }

    private boolean handleReload(final CommandSender sender) {
        try {
            this.actionService.reload();
            sender.sendMessage(ChatColor.GREEN + "wild_economy reloaded.");
        } catch (final RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reload wild_economy", exception);
            sender.sendMessage(ChatColor.RED + "Reload failed. Check console for details.");
        }
        return true;
    }

    private boolean handleCatalogPreview(final CommandSender sender) {
        return this.runCatalogAction(sender, CatalogCommandAction.PREVIEW);
    }

    private boolean handleCatalogValidate(final CommandSender sender) {
        return this.runCatalogAction(sender, CatalogCommandAction.VALIDATE);
    }

    private boolean handleCatalogDiff(final CommandSender sender) {
        return this.runCatalogAction(sender, CatalogCommandAction.DIFF);
    }

    private boolean handleCatalogApply(final CommandSender sender) {
        return this.runCatalogAction(sender, CatalogCommandAction.APPLY);
    }

    private boolean handleItemInspect(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin item <item_key>.");
            return true;
        }

        try {
            final AdminCatalogActionService.ItemInspectResult result = this.actionService.inspectItem(args[1]);
            final List<String> messages = this.summaryFormatter.formatItemInspect(result);
            for (final String message : messages) {
                sender.sendMessage(message);
            }
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to inspect generated catalog item", exception);
            sender.sendMessage(ChatColor.RED + "Item inspect failed: " + exception.getMessage());
            return true;
        }
    }

    private boolean runCatalogAction(final CommandSender sender, final CatalogCommandAction action) {
        try {
            final AdminCatalogActionService.AdminCatalogActionResult result = switch (action) {
                case PREVIEW -> this.actionService.preview();
                case VALIDATE -> this.actionService.validate();
                case DIFF -> this.actionService.diff();
                case APPLY -> this.actionService.apply();
            };

            final List<String> messages = this.summaryFormatter.formatCatalogAction(result, action.includeTopItems());
            for (final String message : messages) {
                sender.sendMessage(message);
            }
            return true;
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to " + action.actionName() + " catalog", exception);
            sender.sendMessage(ChatColor.RED + "Catalog " + action.actionName() + " failed: " + exception.getMessage());
            return true;
        }
    }

    private boolean ensurePermission(
        final CommandSender sender,
        final String permission,
        final String deniedMessage
    ) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(deniedMessage);
        return false;
    }

    private void sendUsage(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin to open the admin review GUI.");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin gui");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin reload");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin catalog <preview|validate|diff|apply>");
        sender.sendMessage(ChatColor.YELLOW + "Use /shopadmin item <item_key>");
    }

    private enum CatalogCommandAction {
        PREVIEW("preview", true),
        VALIDATE("validate", false),
        DIFF("diff", false),
        APPLY("apply", false);

        private final String actionName;
        private final boolean includeTopItems;

        CatalogCommandAction(final String actionName, final boolean includeTopItems) {
            this.actionName = actionName;
            this.includeTopItems = includeTopItems;
        }

        public String actionName() {
            return this.actionName;
        }

        public boolean includeTopItems() {
            return this.includeTopItems;
        }
    }
}

