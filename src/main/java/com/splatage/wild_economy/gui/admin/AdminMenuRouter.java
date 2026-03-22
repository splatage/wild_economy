package com.splatage.wild_economy.gui.admin;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.catalog.admin.AdminCatalogBuildResult;
import com.splatage.wild_economy.catalog.admin.AdminCatalogDecisionTrace;
import com.splatage.wild_economy.catalog.admin.AdminCatalogItemKeys;
import com.splatage.wild_economy.catalog.admin.AdminCatalogManualOverride;
import com.splatage.wild_economy.catalog.admin.AdminCatalogPhaseOneService;
import com.splatage.wild_economy.catalog.admin.AdminCatalogReviewBucket;
import com.splatage.wild_economy.catalog.admin.AdminCatalogRuleImpact;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.platform.PlatformExecutor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AdminMenuRouter {

    private static final String PERMISSION_VIEW = "wild_economy.admin.view";
    private static final String PERMISSION_APPLY = "wild_economy.admin.apply";
    private static final String PERMISSION_OVERRIDE = "wild_economy.admin.override";

    private final WildEconomyPlugin plugin;
    private final PlatformExecutor platformExecutor;
    private final AdminCatalogPhaseOneService catalogService;
    private final AdminManualOverrideEditor manualOverrideEditor;
    private final AdminRootMenu adminRootMenu;
    private final AdminReviewBucketMenu adminReviewBucketMenu;
    private final AdminRuleImpactMenu adminRuleImpactMenu;
    private final AdminItemInspectorMenu adminItemInspectorMenu;
    private final AdminOverrideEditMenu adminOverrideEditMenu;

    public AdminMenuRouter(
        final WildEconomyPlugin plugin,
        final PlatformExecutor platformExecutor,
        final AdminRootMenu adminRootMenu,
        final AdminReviewBucketMenu adminReviewBucketMenu,
        final AdminRuleImpactMenu adminRuleImpactMenu,
        final AdminItemInspectorMenu adminItemInspectorMenu,
        final AdminOverrideEditMenu adminOverrideEditMenu
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "platformExecutor");
        this.catalogService = new AdminCatalogPhaseOneService(plugin);
        this.manualOverrideEditor = new AdminManualOverrideEditor(plugin);
        this.adminRootMenu = Objects.requireNonNull(adminRootMenu, "adminRootMenu");
        this.adminReviewBucketMenu = Objects.requireNonNull(adminReviewBucketMenu, "adminReviewBucketMenu");
        this.adminRuleImpactMenu = Objects.requireNonNull(adminRuleImpactMenu, "adminRuleImpactMenu");
        this.adminItemInspectorMenu = Objects.requireNonNull(adminItemInspectorMenu, "adminItemInspectorMenu");
        this.adminOverrideEditMenu = Objects.requireNonNull(adminOverrideEditMenu, "adminOverrideEditMenu");
    }

    public void openRoot(final Player player) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.rebuildAndOpenRoot(player, "preview", false);
    }

    public void openRoot(final Player player, final AdminCatalogViewState state) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(player, () -> this.adminRootMenu.open(player, state));
    }

    public void rebuildAndOpenRoot(final Player player, final String actionName, final boolean apply) {
        if (apply || this.isApplyConfirmAction(actionName)) {
            if (!this.ensureApplyPermission(player)) {
                return;
            }
        } else if (!this.ensureViewPermission(player)) {
            return;
        }

        try {
            final AdminCatalogViewState state = this.buildState(apply, actionName);
            if (apply) {
                this.sendApplyMessages(player, state);
                this.platformExecutor.runOnPlayer(player, player::closeInventory);
                this.plugin.getBootstrap().reload();
                player.sendMessage(ChatColor.GREEN + "wild_economy reloaded with the published catalog.");
                return;
            }
            if (this.isApplyConfirmAction(actionName)) {
                this.sendApplyConfirmMessage(player, state);
            } else {
                this.sendActionSummary(player, state, actionName);
            }
            this.platformExecutor.runOnPlayer(player, () -> this.adminRootMenu.open(player, state));
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to " + actionName + " catalog from admin GUI", exception);
            player.sendMessage(ChatColor.RED + "Catalog " + actionName + " failed: " + exception.getMessage());
        }
    }

    public void openReviewBucketList(final Player player, final AdminCatalogViewState state) {
        this.openReviewBucketList(player, state, 0, "count");
    }

    public void openReviewBucketList(final Player player, final AdminCatalogViewState state, final int pageIndex, final String sortMode) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(player, () -> this.adminReviewBucketMenu.openList(player, state, pageIndex, sortMode));
    }

    public void openReviewBucketDetail(final Player player, final AdminCatalogViewState state, final String bucketId) {
        this.openReviewBucketDetail(player, state, bucketId, 0, "count");
    }

    public void openReviewBucketDetail(
        final Player player,
        final AdminCatalogViewState state,
        final String bucketId,
        final int pageIndex,
        final String sortMode
    ) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(player, () -> this.adminReviewBucketMenu.openDetail(player, state, bucketId, pageIndex, sortMode));
    }

    public void openReviewBucketSubgroupDetail(
        final Player player,
        final AdminCatalogViewState state,
        final String bucketId,
        final String subgroupId,
        final int pageIndex,
        final String sortMode
    ) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.adminReviewBucketMenu.openSubgroupDetail(player, state, bucketId, subgroupId, pageIndex, sortMode)
        );
    }

    public void openRuleImpactList(final Player player, final AdminCatalogViewState state) {
        this.openRuleImpactList(player, state, 0, "loss");
    }

    public void openRuleImpactList(final Player player, final AdminCatalogViewState state, final int pageIndex, final String sortMode) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(player, () -> this.adminRuleImpactMenu.openList(player, state, pageIndex, sortMode));
    }

    public void openRuleImpactDetail(final Player player, final AdminCatalogViewState state, final String ruleId) {
        this.openRuleImpactDetail(player, state, ruleId, 0, "loss");
    }

    public void openRuleImpactDetail(
        final Player player,
        final AdminCatalogViewState state,
        final String ruleId,
        final int pageIndex,
        final String sortMode
    ) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(player, () -> this.adminRuleImpactMenu.openDetail(player, state, ruleId, pageIndex, sortMode));
    }

    public void openRuleImpactSampleDetail(
        final Player player,
        final AdminCatalogViewState state,
        final String ruleId,
        final String sampleGroupId,
        final int pageIndex,
        final String sortMode
    ) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.adminRuleImpactMenu.openSampleDetail(player, state, ruleId, sampleGroupId, pageIndex, sortMode)
        );
    }

    public void openItemInspector(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode
    ) {
        if (!this.ensureViewPermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.adminItemInspectorMenu.open(player, state, itemKey, returnBucketId, returnRuleId, pageIndex, sortMode)
        );
    }

    public void openOverrideEditor(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode
    ) {
        if (!this.ensureOverridePermission(player)) {
            return;
        }

        final AdminCatalogDecisionTrace trace = state.findTrace(itemKey);
        if (trace == null) {
            player.sendMessage(ChatColor.RED + "No generated catalog decision found for '" + itemKey + "'.");
            this.openRoot(player, state);
            return;
        }

        final AdminCatalogManualOverride override = this.manualOverrideEditor.loadOverride(itemKey);
        final String policy = override != null && override.policy() != null ? override.policy().name() : trace.finalPolicy().name();
        final String stockProfile = override != null && hasText(override.stockProfile()) ? override.stockProfile() : trace.stockProfile();
        final String ecoEnvelope = override != null && hasText(override.ecoEnvelope()) ? override.ecoEnvelope() : trace.ecoEnvelope();
        final String note = override != null ? blankToEmpty(override.note()) : blankToEmpty(trace.note());

        this.openOverrideEditor(player, state, itemKey, returnBucketId, returnRuleId, pageIndex, sortMode, policy, stockProfile, ecoEnvelope, note, null);
    }

    public void openOverrideEditor(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode,
        final String overridePolicy,
        final String overrideStockProfile,
        final String overrideEcoEnvelope,
        final String overrideNote,
        final String actionId
    ) {
        if (!this.ensureOverridePermission(player)) {
            return;
        }
        this.platformExecutor.runOnPlayer(
            player,
            () -> this.adminOverrideEditMenu.open(
                player,
                state,
                itemKey,
                returnBucketId,
                returnRuleId,
                pageIndex,
                sortMode,
                overridePolicy,
                overrideStockProfile,
                overrideEcoEnvelope,
                overrideNote,
                actionId
            )
        );
    }

    public void saveManualOverrideAndInspect(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode,
        final String overridePolicy,
        final String overrideStockProfile,
        final String overrideEcoEnvelope,
        final String overrideNote
    ) {
        if (!this.ensureOverridePermission(player)) {
            return;
        }

        try {
            if (!this.manualOverrideEditor.stockProfileExists(overrideStockProfile)) {
                player.sendMessage(ChatColor.RED + "Unknown stock profile: " + overrideStockProfile);
                return;
            }
            if (!this.manualOverrideEditor.ecoEnvelopeExists(overrideEcoEnvelope)) {
                player.sendMessage(ChatColor.RED + "Unknown eco envelope: " + overrideEcoEnvelope);
                return;
            }

            this.manualOverrideEditor.saveOverride(itemKey, overridePolicy, overrideStockProfile, overrideEcoEnvelope, overrideNote);
            player.sendMessage(ChatColor.GREEN + "Saved manual override for " + AdminCatalogItemKeys.canonicalize(itemKey) + ".");

            final AdminCatalogViewState refreshedState = this.buildState(false, "preview");
            this.openItemInspector(player, refreshedState, itemKey, returnBucketId, returnRuleId, pageIndex, sortMode);
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to save manual override for " + itemKey, exception);
            player.sendMessage(ChatColor.RED + "Failed to save override: " + exception.getMessage());
        }
    }

    public void removeManualOverrideAndInspect(
        final Player player,
        final AdminCatalogViewState state,
        final String itemKey,
        final String returnBucketId,
        final String returnRuleId,
        final int pageIndex,
        final String sortMode
    ) {
        if (!this.ensureOverridePermission(player)) {
            return;
        }

        try {
            final boolean removed = this.manualOverrideEditor.removeOverride(itemKey);
            if (removed) {
                player.sendMessage(ChatColor.GREEN + "Removed manual override for " + AdminCatalogItemKeys.canonicalize(itemKey) + ".");
            } else {
                player.sendMessage(ChatColor.YELLOW + "No manual override existed for " + AdminCatalogItemKeys.canonicalize(itemKey) + ".");
            }

            final AdminCatalogViewState refreshedState = this.buildState(false, "preview");
            this.openItemInspector(player, refreshedState, itemKey, returnBucketId, returnRuleId, pageIndex, sortMode);
        } catch (final IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to remove manual override for " + itemKey, exception);
            player.sendMessage(ChatColor.RED + "Failed to remove override: " + exception.getMessage());
        }
    }

    public List<String> availablePolicyIds() {
        return this.manualOverrideEditor.loadPolicyProfileIds();
    }

    public String policyBehaviorSummary(final String policyId) {
        return this.manualOverrideEditor.policyBehaviorSummary(policyId);
    }

    public String nextPolicy(final String currentPolicy) {
        return this.manualOverrideEditor.nextPolicy(currentPolicy);
    }

    public String nextStockProfile(final String currentProfile) {
        return this.manualOverrideEditor.nextNamedValue(this.availableStockProfiles(), currentProfile);
    }

    public String nextEcoEnvelope(final String currentEnvelope) {
        return this.manualOverrideEditor.nextNamedValue(this.availableEcoEnvelopes(), currentEnvelope);
    }

    public String nextOverrideNote(final String currentNote) {
        return this.manualOverrideEditor.nextNote(currentNote);
    }

    public List<String> availableStockProfiles() {
        return this.manualOverrideEditor.loadStockProfileNames();
    }

    public List<String> availableEcoEnvelopes() {
        return this.manualOverrideEditor.loadEcoEnvelopeNames();
    }

    public void goBack(final Player player) {
        if (!this.ensureViewPermission(player)) {
            return;
        }

        final AdminMenuHolder holder = this.currentHolder(player);
        if (holder == null) {
            this.openRoot(player);
            return;
        }

        switch (holder.viewType()) {
            case ROOT -> this.openRoot(player);
            case REVIEW_BUCKET_LIST -> this.openRoot(player, holder.state());
            case REVIEW_BUCKET_DETAIL -> this.openReviewBucketList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            case REVIEW_BUCKET_SUBGROUP_DETAIL -> this.openReviewBucketDetail(player, holder.state(), holder.bucketId(), holder.pageIndex(), holder.sortMode());
            case RULE_IMPACT_LIST -> this.openRoot(player, holder.state());
            case RULE_IMPACT_DETAIL -> this.openRuleImpactList(player, holder.state(), holder.pageIndex(), holder.sortMode());
            case RULE_IMPACT_SAMPLE_DETAIL -> this.openRuleImpactDetail(player, holder.state(), holder.ruleId(), holder.pageIndex(), holder.sortMode());
            case ITEM_INSPECTOR -> {
                if (holder.returnBucketId() != null) {
                    this.openReviewBucketDetail(player, holder.state(), holder.returnBucketId(), holder.pageIndex(), holder.sortMode());
                } else if (holder.returnRuleId() != null) {
                    this.openRuleImpactDetail(player, holder.state(), holder.returnRuleId(), holder.pageIndex(), holder.sortMode());
                } else {
                    this.openRoot(player, holder.state());
                }
            }
            case OVERRIDE_EDITOR -> this.openItemInspector(
                player,
                holder.state(),
                holder.itemKey(),
                holder.returnBucketId(),
                holder.returnRuleId(),
                holder.pageIndex(),
                holder.sortMode()
            );
        }
    }

    public void closeAllAdminViews() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (this.currentHolder(player) == null) {
                continue;
            }
            this.platformExecutor.runOnPlayer(player, player::closeInventory);
        }
    }

    public static AdminMenuHolder getAdminMenuHolder(final Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        final InventoryHolder holder = inventory.getHolder();
        if (holder instanceof AdminMenuHolder adminMenuHolder) {
            return adminMenuHolder;
        }
        return null;
    }

    private AdminMenuHolder currentHolder(final Player player) {
        return getAdminMenuHolder(player.getOpenInventory().getTopInventory());
    }

    private AdminCatalogViewState buildState(final boolean apply, final String actionName) throws IOException {
        final AdminCatalogBuildResult buildResult = this.catalogService.build(apply);
        final File generatedDirectory = buildResult.generatedDirectory();
        final List<AdminCatalogRuleImpact> ruleImpacts = this.loadRuleImpacts(new File(generatedDirectory, "generated-rule-impacts.yml"));
        final List<AdminCatalogReviewBucket> reviewBuckets = this.loadReviewBuckets(new File(generatedDirectory, "generated-review-buckets.yml"));
        return new AdminCatalogViewState(buildResult, ruleImpacts, reviewBuckets, actionName);
    }

    private List<AdminCatalogRuleImpact> loadRuleImpacts(final File file) {
        if (!file.isFile()) {
            return List.of();
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection rulesSection = yaml.getConfigurationSection("rules");
        if (rulesSection == null) {
            return List.of();
        }

        final List<AdminCatalogRuleImpact> ruleImpacts = new ArrayList<>();
        for (final String ruleId : rulesSection.getKeys(false)) {
            final ConfigurationSection section = rulesSection.getConfigurationSection(ruleId);
            if (section == null) {
                continue;
            }
            ruleImpacts.add(
                new AdminCatalogRuleImpact(
                    ruleId,
                    section.getBoolean("fallback-rule"),
                    section.getBoolean("has-match-criteria"),
                    section.getInt("match-count"),
                    section.getInt("win-count"),
                    section.getInt("loss-count"),
                    this.loadPolicyCounts(section.getConfigurationSection("winning-policies")),
                    this.loadPolicyCounts(section.getConfigurationSection("lost-to-policies")),
                    this.loadStringCounts(section.getConfigurationSection("lost-to-rules")),
                    section.getStringList("sample-matched-items"),
                    section.getStringList("sample-winning-items"),
                    section.getStringList("sample-lost-items")
                )
            );
        }
        return ruleImpacts;
    }

    private List<AdminCatalogReviewBucket> loadReviewBuckets(final File file) {
        if (!file.isFile()) {
            return List.of();
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection bucketsSection = yaml.getConfigurationSection("buckets");
        if (bucketsSection == null) {
            return List.of();
        }

        final List<AdminCatalogReviewBucket> reviewBuckets = new ArrayList<>();
        for (final String bucketId : bucketsSection.getKeys(false)) {
            final ConfigurationSection section = bucketsSection.getConfigurationSection(bucketId);
            if (section == null) {
                continue;
            }
            reviewBuckets.add(
                new AdminCatalogReviewBucket(
                    bucketId,
                    section.getString("description", ""),
                    section.getInt("count"),
                    section.getStringList("sample-items"),
                    this.loadStringCounts(section.getConfigurationSection("subgroup-counts")),
                    this.loadSampleMap(section.getConfigurationSection("subgroup-sample-items"))
                )
            );
        }

        reviewBuckets.sort(Comparator.comparingInt(AdminCatalogReviewBucket::count).reversed());
        return reviewBuckets;
    }

    private Map<CatalogPolicy, Integer> loadPolicyCounts(final ConfigurationSection section) {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, 0);
        }
        if (section == null) {
            return counts;
        }

        for (final String key : section.getKeys(false)) {
            try {
                counts.put(CatalogPolicy.valueOf(key.toUpperCase(Locale.ROOT)), section.getInt(key));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return counts;
    }

    private Map<String, Integer> loadStringCounts(final ConfigurationSection section) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (section == null) {
            return counts;
        }
        for (final String key : section.getKeys(false)) {
            counts.put(key, section.getInt(key));
        }
        return counts;
    }

    private Map<String, List<String>> loadSampleMap(final ConfigurationSection section) {
        final Map<String, List<String>> sampleMap = new LinkedHashMap<>();
        if (section == null) {
            return sampleMap;
        }
        for (final String key : section.getKeys(false)) {
            sampleMap.put(key, List.copyOf(section.getStringList(key)));
        }
        return sampleMap;
    }

    private void sendActionSummary(final Player player, final AdminCatalogViewState state, final String actionName) {
        final AdminCatalogBuildResult result = state.buildResult();
        player.sendMessage(
            ChatColor.GOLD
                + "Catalog "
                + actionName
                + " complete: scanned "
                + result.totalScanned()
                + ", proposed "
                + result.proposedEntries().size()
                + ", live-enabled "
                + result.liveEntries().size()
                + "."
        );
        player.sendMessage(
            ChatColor.YELLOW
                + "Warnings "
                + result.warningCount()
                + ", errors "
                + result.errorCount()
                + ". Reports written to "
                + result.generatedDirectory().getPath()
                + "."
        );
    }

    private boolean isApplyConfirmAction(final String actionName) {
        return "apply-confirm".equalsIgnoreCase(actionName);
    }

    private void sendApplyConfirmMessage(final Player player, final AdminCatalogViewState state) {
        final AdminCatalogBuildResult result = state.buildResult();
        player.sendMessage(
            ChatColor.RED
                + "Apply armed: "
                + result.liveEntries().size()
                + " live items, "
                + result.warningCount()
                + " warnings, "
                + result.errorCount()
                + " errors."
        );
        player.sendMessage(ChatColor.YELLOW + "Click Confirm Apply in the GUI to publish, or Cancel Apply to return.");
    }

    private void sendApplyMessages(final Player player, final AdminCatalogViewState state) {
        final AdminCatalogBuildResult result = state.buildResult();
        player.sendMessage(ChatColor.GREEN + "Published catalog written to " + result.liveCatalogFile().getPath());
        player.sendMessage(ChatColor.GREEN + "Generated reports written to " + result.generatedDirectory().getPath());
        if (result.snapshotDirectory() != null) {
            player.sendMessage(ChatColor.GREEN + "Snapshot created at " + result.snapshotDirectory().getPath());
        }
        player.sendMessage(ChatColor.YELLOW + "Reloading plugin to apply the new live catalog...");
    }

    private boolean ensureViewPermission(final Player player) {
        return this.ensurePermission(
            player,
            PERMISSION_VIEW,
            ChatColor.RED + "You do not have permission to view wild_economy admin screens."
        );
    }

    private boolean ensureApplyPermission(final Player player) {
        return this.ensurePermission(
            player,
            PERMISSION_APPLY,
            ChatColor.RED + "You do not have permission to publish the generated live catalog."
        );
    }

    private boolean ensureOverridePermission(final Player player) {
        return this.ensurePermission(
            player,
            PERMISSION_OVERRIDE,
            ChatColor.RED + "You do not have permission to edit manual catalog overrides."
        );
    }

    private boolean ensurePermission(final Player player, final String permission, final String message) {
        if (player.hasPermission(permission)) {
            return true;
        }
        player.sendMessage(message);
        return false;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToEmpty(final String value) {
        return value == null ? "" : value;
    }
}

