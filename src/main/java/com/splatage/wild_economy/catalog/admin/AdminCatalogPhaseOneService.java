package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.classify.DefaultCategoryClassifier;
import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.derive.RootAnchoredDerivationService;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import com.splatage.wild_economy.catalog.policy.DefaultPolicySuggestionService;
import com.splatage.wild_economy.catalog.recipe.BukkitRecipeGraphBuilder;
import com.splatage.wild_economy.catalog.recipe.RecipeGraph;
import com.splatage.wild_economy.catalog.rootvalue.RootValueLoader;
import com.splatage.wild_economy.catalog.scan.BukkitMaterialScanner;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminCatalogPhaseOneService {

    private static final int DEFAULT_MAX_AUTO_INCLUSION_DEPTH = 1;
    private static final DateTimeFormatter SNAPSHOT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
    private final JavaPlugin plugin;

    public AdminCatalogPhaseOneService(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public AdminCatalogBuildResult build(final boolean apply) throws IOException {
        final File dataFolder = this.plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Failed to create data folder: " + dataFolder.getAbsolutePath());
        }

        final File rootValuesFile = new File(dataFolder, "root-values.yml");
        if (!rootValuesFile.isFile()) {
            throw new IOException("root-values.yml not found at " + rootValuesFile.getAbsolutePath());
        }

        final RootValueLoader rootValues = RootValueLoader.fromFile(rootValuesFile);
        final RecipeGraph recipeGraph = new BukkitRecipeGraphBuilder().build();
        final RootAnchoredDerivationService derivationService = new RootAnchoredDerivationService(recipeGraph, rootValues);
        final BukkitMaterialScanner materialScanner = new BukkitMaterialScanner(rootValues);
        final DefaultCategoryClassifier classifier = new DefaultCategoryClassifier();
        final DefaultPolicySuggestionService basePolicyService = new DefaultPolicySuggestionService(DEFAULT_MAX_AUTO_INCLUSION_DEPTH);

        final List<AdminCatalogPolicyRule> rules = this.loadPolicyRules(new File(dataFolder, "policy-rules.yml"));
        final Map<String, AdminCatalogManualOverride> overrides = this.loadManualOverrides(new File(dataFolder, "manual-overrides.yml"));
        final Map<String, AdminCatalogStockProfile> stockProfiles = this.loadStockProfiles(new File(dataFolder, "stock-profiles.yml"));
        final Map<String, AdminCatalogEcoEnvelope> ecoEnvelopes = this.loadEcoEnvelopes(new File(dataFolder, "eco-envelopes.yml"));

        final List<AdminCatalogPlanEntry> proposedEntries = new ArrayList<>();
        final List<AdminCatalogDecisionTrace> decisionTraces = new ArrayList<>();
        final List<AdminCatalogValidationIssue> validationIssues = new ArrayList<>();

        int disabledCount = 0;
        int unresolvedCount = 0;

        for (final ItemFacts facts : materialScanner.scanAll()) {
            final CatalogCategory baseCategory = classifier.classify(facts);
            final DerivedItemResult derivation = derivationService.resolve(facts.key());
            CatalogPolicy policy = basePolicyService.suggest(facts, baseCategory, derivation);
            CatalogCategory finalCategory = baseCategory;

            final List<String> matchedRuleIds = new ArrayList<>();
            String winningRuleId = null;
            String stockProfileName = this.defaultStockProfileName(policy);
            String ecoEnvelopeName = this.defaultEcoEnvelopeName(policy);
            String note = null;

            for (final AdminCatalogPolicyRule rule : rules) {
                if (!rule.matches(facts, finalCategory, derivation)) {
                    continue;
                }
                matchedRuleIds.add(rule.id());
                winningRuleId = rule.id();
                if (rule.policy() != null) {
                    policy = rule.policy();
                }
                if (rule.stockProfile() != null && !rule.stockProfile().isBlank()) {
                    stockProfileName = rule.stockProfile();
                }
                if (rule.ecoEnvelope() != null && !rule.ecoEnvelope().isBlank()) {
                    ecoEnvelopeName = rule.ecoEnvelope();
                }
                if (rule.note() != null && !rule.note().isBlank()) {
                    note = rule.note();
                }
            }

            final AdminCatalogManualOverride override = overrides.get(facts.key());
            if (override != null) {
                if (override.policy() != null) {
                    policy = override.policy();
                }
                if (override.category() != null) {
                    finalCategory = override.category();
                }
                if (override.stockProfile() != null && !override.stockProfile().isBlank()) {
                    stockProfileName = override.stockProfile();
                }
                if (override.ecoEnvelope() != null && !override.ecoEnvelope().isBlank()) {
                    ecoEnvelopeName = override.ecoEnvelope();
                }
                if (override.note() != null && !override.note().isBlank()) {
                    note = override.note();
                }
            }

            if (policy != CatalogPolicy.DISABLED && !derivation.rootValuePresent() && derivation.derivedValue() == null) {
                validationIssues.add(
                    new AdminCatalogValidationIssue(
                        AdminCatalogValidationIssue.Severity.WARNING,
                        facts.key() + " has no rooted value path; final policy forced to DISABLED."
                    )
                );
                policy = CatalogPolicy.DISABLED;
                unresolvedCount++;
            }

            final AdminCatalogStockProfile stockProfile = stockProfiles.get(stockProfileName);
            final AdminCatalogEcoEnvelope ecoEnvelope = ecoEnvelopes.get(ecoEnvelopeName);

            if (stockProfile == null) {
                validationIssues.add(
                    new AdminCatalogValidationIssue(
                        AdminCatalogValidationIssue.Severity.ERROR,
                        facts.key() + " references missing stock profile '" + stockProfileName + "'."
                    )
                );
            }
            if (ecoEnvelope == null) {
                validationIssues.add(
                    new AdminCatalogValidationIssue(
                        AdminCatalogValidationIssue.Severity.ERROR,
                        facts.key() + " references missing eco envelope '" + ecoEnvelopeName + "'."
                    )
                );
            }

            final BigDecimal anchorValue = this.resolveAnchorValue(derivation);
            final BigDecimal buyPrice = this.computeBuyPrice(anchorValue, ecoEnvelope);
            final BigDecimal sellPrice = this.computeSellPrice(anchorValue, ecoEnvelope);

            final String runtimePolicy = this.toRuntimePolicy(policy);
            final boolean buyEnabled = policy == CatalogPolicy.ALWAYS_AVAILABLE || policy == CatalogPolicy.EXCHANGE;
            final boolean sellEnabled = policy == CatalogPolicy.EXCHANGE || policy == CatalogPolicy.SELL_ONLY;

            final AdminCatalogPlanEntry planEntry = new AdminCatalogPlanEntry(
                facts.key(),
                this.buildDisplayName(facts.material()),
                finalCategory,
                policy,
                runtimePolicy,
                buyEnabled,
                sellEnabled,
                stockProfile == null ? 0 : stockProfile.stockCap(),
                stockProfile == null ? 0 : stockProfile.turnoverAmountPerInterval(),
                anchorValue,
                buyPrice,
                sellPrice,
                stockProfileName,
                ecoEnvelopeName,
                derivation.reason().name(),
                derivation.derivationDepth(),
                policy == CatalogPolicy.DISABLED ? null : "policy=" + policy.name().toLowerCase(Locale.ROOT),
                policy == CatalogPolicy.DISABLED ? "disabled" : null,
                this.joinNotes(note, facts)
            );
            proposedEntries.add(planEntry);

            decisionTraces.add(
                new AdminCatalogDecisionTrace(
                    facts.key(),
                    baseCategory,
                    derivation.reason(),
                    derivation.derivationDepth(),
                    derivation.rootValuePresent(),
                    derivation.rootValue(),
                    derivation.derivedValue(),
                    basePolicyService.suggest(facts, baseCategory, derivation),
                    matchedRuleIds,
                    winningRuleId,
                    override != null,
                    policy,
                    finalCategory,
                    stockProfileName,
                    ecoEnvelopeName,
                    this.buildDisplayName(facts.material()),
                    note
                )
            );

            if (policy == CatalogPolicy.DISABLED) {
                disabledCount++;
            }
        }

        proposedEntries.sort(Comparator.comparing(AdminCatalogPlanEntry::category).thenComparing(AdminCatalogPlanEntry::itemKey));
        decisionTraces.sort(Comparator.comparing(AdminCatalogDecisionTrace::itemKey));

        final File generatedDirectory = new File(dataFolder, "generated");
        if (!generatedDirectory.exists() && !generatedDirectory.mkdirs()) {
            throw new IOException("Failed to create generated directory: " + generatedDirectory.getAbsolutePath());
        }

        final File liveCatalogFile = new File(dataFolder, "exchange-items.yml");
        final Map<String, LiveSnapshotEntry> currentLive = this.loadLiveSnapshot(liveCatalogFile);
        final List<AdminCatalogPlanEntry> liveEntries = proposedEntries.stream()
            .filter(entry -> entry.policy() != CatalogPolicy.DISABLED)
            .toList();
        final List<AdminCatalogDiffEntry> diffEntries = this.diff(currentLive, liveEntries);

        this.writeGeneratedOutputs(
            generatedDirectory,
            proposedEntries,
            liveEntries,
            decisionTraces,
            diffEntries,
            validationIssues
        );

        final long warningCount = validationIssues.stream()
            .filter(issue -> issue.severity() == AdminCatalogValidationIssue.Severity.WARNING)
            .count();
        final long errorCount = validationIssues.stream()
            .filter(issue -> issue.severity() == AdminCatalogValidationIssue.Severity.ERROR)
            .count();

        if (apply && errorCount > 0) {
            throw new IOException("Validation errors prevent apply. Review generated/generated-validation.yml.");
        }

        File snapshotDirectory = null;
        if (apply) {
            snapshotDirectory = this.snapshotCurrentState(dataFolder);
            this.writeLiveCatalog(liveCatalogFile, liveEntries, ecoEnvelopes);
        }

        return new AdminCatalogBuildResult(
            proposedEntries,
            liveEntries,
            decisionTraces,
            diffEntries,
            validationIssues,
            generatedDirectory,
            liveCatalogFile,
            snapshotDirectory,
            proposedEntries.size(),
            disabledCount,
            unresolvedCount,
            (int) warningCount,
            (int) errorCount
        );
    }

    private List<AdminCatalogPolicyRule> loadPolicyRules(final File file) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final List<Map<?, ?>> rawRules = yaml.getMapList("rules");
        final List<AdminCatalogPolicyRule> rules = new ArrayList<>(rawRules.size());

        int syntheticIndex = 1;
        for (final Map<?, ?> rawRule : rawRules) {
            final ConfigurationSection ruleSection = this.asSection(rawRule);
            if (ruleSection == null) {
                continue;
            }
            final ConfigurationSection match = ruleSection.getConfigurationSection("match");
            final ConfigurationSection set = ruleSection.getConfigurationSection("set");

            final String id = ruleSection.getString("id", "rule-" + syntheticIndex++);
            rules.add(
                new AdminCatalogPolicyRule(
                    id,
                    normalizeKeys(match == null ? List.of() : match.getStringList("item-keys")),
                    normalizeKeys(match == null ? List.of() : match.getStringList("item-key-patterns")),
                    parseCategories(match == null ? List.of() : match.getStringList("categories")),
                    parseDerivationReasons(match == null ? List.of() : match.getStringList("derivation-reasons")),
                    match == null || !match.contains("min-derivation-depth") ? null : Integer.valueOf(match.getInt("min-derivation-depth")),
                    match == null || !match.contains("max-derivation-depth") ? null : Integer.valueOf(match.getInt("max-derivation-depth")),
                    match == null || !match.contains("root-value-present") ? null : Boolean.valueOf(match.getBoolean("root-value-present")),
                    parsePolicy(set == null ? null : set.getString("policy")),
                    set == null ? null : set.getString("stock-profile"),
                    set == null ? null : set.getString("eco-envelope"),
                    set == null ? null : set.getString("note")
                )
            );
        }
        return rules;
    }

    private Map<String, AdminCatalogManualOverride> loadManualOverrides(final File file) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection overridesSection = yaml.getConfigurationSection("overrides");
        final Map<String, AdminCatalogManualOverride> overrides = new LinkedHashMap<>();
        if (overridesSection == null) {
            return overrides;
        }

        for (final String itemKey : overridesSection.getKeys(false)) {
            final ConfigurationSection section = overridesSection.getConfigurationSection(itemKey);
            if (section == null) {
                continue;
            }
            overrides.put(
                itemKey.toLowerCase(Locale.ROOT),
                new AdminCatalogManualOverride(
                    itemKey.toLowerCase(Locale.ROOT),
                    parsePolicy(section.getString("policy")),
                    parseCategory(section.getString("category")),
                    section.getString("stock-profile"),
                    section.getString("eco-envelope"),
                    section.getString("note")
                )
            );
        }

        return overrides;
    }

    private Map<String, AdminCatalogStockProfile> loadStockProfiles(final File file) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection section = yaml.getConfigurationSection("stock-profiles");
        final Map<String, AdminCatalogStockProfile> profiles = new LinkedHashMap<>();
        if (section == null) {
            return profiles;
        }

        for (final String name : section.getKeys(false)) {
            final ConfigurationSection profileSection = section.getConfigurationSection(name);
            if (profileSection == null) {
                continue;
            }
            profiles.put(
                name,
                new AdminCatalogStockProfile(
                    name,
                    profileSection.getInt("stock-cap", 0),
                    profileSection.getInt("turnover-amount-per-interval", 0),
                    profileSection.getInt("low-stock-threshold", 0),
                    profileSection.getInt("overflow-threshold", 0)
                )
            );
        }

        return profiles;
    }

    private Map<String, AdminCatalogEcoEnvelope> loadEcoEnvelopes(final File file) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection section = yaml.getConfigurationSection("eco-envelopes");
        final Map<String, AdminCatalogEcoEnvelope> envelopes = new LinkedHashMap<>();
        if (section == null) {
            return envelopes;
        }

        for (final String name : section.getKeys(false)) {
            final ConfigurationSection envelopeSection = section.getConfigurationSection(name);
            if (envelopeSection == null) {
                continue;
            }
            final List<AdminCatalogSellBand> sellBands = new ArrayList<>();
            for (final Map<?, ?> rawBand : envelopeSection.getMapList("sell-bands")) {
                final ConfigurationSection bandSection = this.asSection(rawBand);
                if (bandSection == null) {
                    continue;
                }
                sellBands.add(
                    new AdminCatalogSellBand(
                        bandSection.getDouble("min-fill", 0.0D),
                        bandSection.getDouble("max-fill", 1.01D),
                        bandSection.getDouble("multiplier", 1.0D)
                    )
                );
            }
            if (sellBands.isEmpty()) {
                sellBands.add(new AdminCatalogSellBand(0.0D, 1.01D, 1.0D));
            }

            envelopes.put(
                name,
                new AdminCatalogEcoEnvelope(
                    name,
                    envelopeSection.getDouble("base-buy-multiplier", 1.0D),
                    envelopeSection.getDouble("base-sell-multiplier", 0.67D),
                    sellBands
                )
            );
        }

        return envelopes;
    }

    private List<AdminCatalogDiffEntry> diff(
        final Map<String, LiveSnapshotEntry> currentLive,
        final List<AdminCatalogPlanEntry> proposedLive
    ) {
        final Map<String, AdminCatalogPlanEntry> proposedByKey = new LinkedHashMap<>();
        for (final AdminCatalogPlanEntry entry : proposedLive) {
            proposedByKey.put(entry.itemKey(), entry);
        }

        final List<AdminCatalogDiffEntry> diffEntries = new ArrayList<>();
        for (final AdminCatalogPlanEntry entry : proposedLive) {
            final LiveSnapshotEntry current = currentLive.get(entry.itemKey());
            if (current == null) {
                diffEntries.add(new AdminCatalogDiffEntry(AdminCatalogDiffEntry.ChangeType.ADDED, entry.itemKey(), "new live item"));
                continue;
            }
            if (!current.matches(entry)) {
                diffEntries.add(new AdminCatalogDiffEntry(AdminCatalogDiffEntry.ChangeType.CHANGED, entry.itemKey(), current.describeChange(entry)));
            }
        }
        for (final String itemKey : currentLive.keySet()) {
            if (!proposedByKey.containsKey(itemKey)) {
                diffEntries.add(new AdminCatalogDiffEntry(AdminCatalogDiffEntry.ChangeType.REMOVED, itemKey, "removed from live catalog"));
            }
        }
        diffEntries.sort(Comparator.comparing(AdminCatalogDiffEntry::itemKey));
        return diffEntries;
    }

    private Map<String, LiveSnapshotEntry> loadLiveSnapshot(final File file) {
        final Map<String, LiveSnapshotEntry> snapshot = new LinkedHashMap<>();
        if (!file.isFile()) {
            return snapshot;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items == null) {
            return snapshot;
        }

        for (final String itemKey : items.getKeys(false)) {
            final ConfigurationSection section = items.getConfigurationSection(itemKey);
            if (section == null) {
                continue;
            }
            snapshot.put(
                itemKey.toLowerCase(Locale.ROOT),
                new LiveSnapshotEntry(
                    itemKey.toLowerCase(Locale.ROOT),
                    section.getString("category", ""),
                    section.getString("policy", ""),
                    section.getBoolean("buy-enabled", false),
                    section.getBoolean("sell-enabled", false),
                    section.getInt("stock-cap", 0),
                    BigDecimal.valueOf(section.getDouble("buy-price", 0.0D)).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(section.getDouble("sell-price", 0.0D)).setScale(2, RoundingMode.HALF_UP)
                )
            );
        }

        return snapshot;
    }

    private void writeGeneratedOutputs(
        final File generatedDirectory,
        final List<AdminCatalogPlanEntry> proposedEntries,
        final List<AdminCatalogPlanEntry> liveEntries,
        final List<AdminCatalogDecisionTrace> decisionTraces,
        final List<AdminCatalogDiffEntry> diffEntries,
        final List<AdminCatalogValidationIssue> validationIssues
    ) throws IOException {
        this.writeGeneratedCatalog(new File(generatedDirectory, "generated-catalog.yml"), proposedEntries);
        this.writeGeneratedSummary(new File(generatedDirectory, "generated-summary.yml"), proposedEntries, liveEntries);
        this.writeGeneratedDiff(new File(generatedDirectory, "generated-diff.yml"), diffEntries);
        this.writeGeneratedValidation(new File(generatedDirectory, "generated-validation.yml"), validationIssues);
        this.writeDecisionTraces(new File(generatedDirectory, "item-decision-traces.yml"), decisionTraces);
    }

    private void writeGeneratedCatalog(final File file, final List<AdminCatalogPlanEntry> entries) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final AdminCatalogPlanEntry entry : entries) {
            final String base = "items." + entry.itemKey();
            yaml.set(base + ".display-name", entry.displayName());
            yaml.set(base + ".category", entry.category().name());
            yaml.set(base + ".admin-policy", entry.policy().name());
            yaml.set(base + ".runtime-policy", entry.runtimePolicy());
            yaml.set(base + ".stock-profile", entry.stockProfile());
            yaml.set(base + ".eco-envelope", entry.ecoEnvelope());
            yaml.set(base + ".buy-enabled", entry.buyEnabled());
            yaml.set(base + ".sell-enabled", entry.sellEnabled());
            yaml.set(base + ".stock-cap", entry.stockCap());
            yaml.set(base + ".turnover-amount-per-interval", entry.turnoverAmountPerInterval());
            yaml.set(base + ".anchor-value", decimal(entry.anchorValue()));
            yaml.set(base + ".buy-price", decimal(entry.buyPrice()));
            yaml.set(base + ".sell-price", decimal(entry.sellPrice()));
            yaml.set(base + ".derivation-reason", entry.derivationReason());
            yaml.set(base + ".derivation-depth", entry.derivationDepth());
            yaml.set(base + ".include-reason", entry.includeReason());
            yaml.set(base + ".exclude-reason", entry.excludeReason());
            yaml.set(base + ".notes", entry.notes());
        }
        yaml.save(file);
    }

    private void writeGeneratedSummary(
        final File file,
        final List<AdminCatalogPlanEntry> proposedEntries,
        final List<AdminCatalogPlanEntry> liveEntries
    ) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("counts.proposed-total", proposedEntries.size());
        yaml.set("counts.live-total", liveEntries.size());

        final Map<CatalogPolicy, Integer> policyCounts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            policyCounts.put(policy, 0);
        }
        for (final AdminCatalogPlanEntry entry : proposedEntries) {
            policyCounts.compute(entry.policy(), (ignored, value) -> value + 1);
        }
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            yaml.set("counts.policy." + policy.name(), policyCounts.get(policy));
        }

        final Map<CatalogCategory, Integer> categoryCounts = new EnumMap<>(CatalogCategory.class);
        for (final CatalogCategory category : CatalogCategory.values()) {
            categoryCounts.put(category, 0);
        }
        for (final AdminCatalogPlanEntry entry : proposedEntries) {
            categoryCounts.compute(entry.category(), (ignored, value) -> value + 1);
        }
        for (final CatalogCategory category : CatalogCategory.values()) {
            yaml.set("counts.category." + category.name(), categoryCounts.get(category));
        }

        yaml.save(file);
    }

    private void writeGeneratedDiff(final File file, final List<AdminCatalogDiffEntry> diffEntries) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (final AdminCatalogDiffEntry entry : diffEntries) {
            final String base = "diff." + index++;
            yaml.set(base + ".item-key", entry.itemKey());
            yaml.set(base + ".change-type", entry.changeType().name());
            yaml.set(base + ".summary", entry.summary());
        }
        yaml.save(file);
    }

    private void writeGeneratedValidation(final File file, final List<AdminCatalogValidationIssue> issues) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (final AdminCatalogValidationIssue issue : issues) {
            final String base = "issues." + index++;
            yaml.set(base + ".severity", issue.severity().name());
            yaml.set(base + ".message", issue.message());
        }
        yaml.save(file);
    }

    private void writeDecisionTraces(final File file, final List<AdminCatalogDecisionTrace> traces) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final AdminCatalogDecisionTrace trace : traces) {
            final String base = "items." + trace.itemKey();
            yaml.set(base + ".classified-category", trace.classifiedCategory().name());
            yaml.set(base + ".derivation-reason", trace.derivationReason().name());
            yaml.set(base + ".derivation-depth", trace.derivationDepth());
            yaml.set(base + ".root-value-present", trace.rootValuePresent());
            yaml.set(base + ".root-value", decimal(trace.rootValue()));
            yaml.set(base + ".derived-value", decimal(trace.derivedValue()));
            yaml.set(base + ".base-suggested-policy", trace.baseSuggestedPolicy().name());
            yaml.set(base + ".matched-rule-ids", trace.matchedRuleIds());
            yaml.set(base + ".winning-rule-id", trace.winningRuleId());
            yaml.set(base + ".manual-override-applied", trace.manualOverrideApplied());
            yaml.set(base + ".final-policy", trace.finalPolicy().name());
            yaml.set(base + ".final-category", trace.finalCategory().name());
            yaml.set(base + ".stock-profile", trace.stockProfile());
            yaml.set(base + ".eco-envelope", trace.ecoEnvelope());
            yaml.set(base + ".display-name", trace.displayName());
            yaml.set(base + ".note", trace.note());
        }
        yaml.save(file);
    }

    private void writeLiveCatalog(
        final File file,
        final List<AdminCatalogPlanEntry> liveEntries,
        final Map<String, AdminCatalogEcoEnvelope> ecoEnvelopes
    ) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final AdminCatalogPlanEntry entry : liveEntries) {
            final String base = "items." + entry.itemKey();
            yaml.set(base + ".display-name", entry.displayName());
            yaml.set(base + ".category", entry.category().name());
            yaml.set(base + ".policy", entry.runtimePolicy());
            yaml.set(base + ".buy-enabled", entry.buyEnabled());
            yaml.set(base + ".sell-enabled", entry.sellEnabled());
            yaml.set(base + ".stock-cap", entry.stockCap());
            yaml.set(base + ".turnover-amount-per-interval", entry.turnoverAmountPerInterval());
            yaml.set(base + ".buy-price", decimal(entry.buyPrice()));
            yaml.set(base + ".sell-price", decimal(entry.sellPrice()));

            final AdminCatalogEcoEnvelope ecoEnvelope = ecoEnvelopes.get(entry.ecoEnvelope());
            final List<AdminCatalogSellBand> sellBands = ecoEnvelope == null
                ? List.of(new AdminCatalogSellBand(0.0D, 1.01D, 1.0D))
                : ecoEnvelope.sellBands();

            int bandIndex = 0;
            for (final AdminCatalogSellBand band : sellBands) {
                final String bandBase = base + ".sell-price-bands." + bandIndex++;
                yaml.set(bandBase + ".min-fill", band.minFill());
                yaml.set(bandBase + ".max-fill", band.maxFill());
                yaml.set(bandBase + ".multiplier", band.multiplier());
            }
        }
        yaml.save(file);
    }

    private File snapshotCurrentState(final File dataFolder) throws IOException {
        final File snapshotsDirectory = new File(dataFolder, "snapshots");
        if (!snapshotsDirectory.exists() && !snapshotsDirectory.mkdirs()) {
            throw new IOException("Failed to create snapshots directory: " + snapshotsDirectory.getAbsolutePath());
        }

        final File snapshot = new File(snapshotsDirectory, SNAPSHOT_FORMAT.format(LocalDateTime.now()));
        if (!snapshot.mkdirs()) {
            throw new IOException("Failed to create snapshot directory: " + snapshot.getAbsolutePath());
        }

        for (final String fileName : List.of(
            "exchange-items.yml",
            "policy-rules.yml",
            "manual-overrides.yml",
            "stock-profiles.yml",
            "eco-envelopes.yml"
        )) {
            final File source = new File(dataFolder, fileName);
            if (!source.isFile()) {
                continue;
            }
            Files.copy(source.toPath(), new File(snapshot, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return snapshot;
    }

    private BigDecimal resolveAnchorValue(final DerivedItemResult derivation) {
        if (derivation.derivedValue() != null) {
            return derivation.derivedValue().setScale(2, RoundingMode.HALF_UP);
        }
        if (derivation.rootValue() != null) {
            return derivation.rootValue().setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private BigDecimal computeBuyPrice(final BigDecimal anchorValue, final AdminCatalogEcoEnvelope ecoEnvelope) {
        if (anchorValue == null || ecoEnvelope == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return anchorValue.multiply(BigDecimal.valueOf(ecoEnvelope.baseBuyMultiplier())).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeSellPrice(final BigDecimal anchorValue, final AdminCatalogEcoEnvelope ecoEnvelope) {
        if (anchorValue == null || ecoEnvelope == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return anchorValue.multiply(BigDecimal.valueOf(ecoEnvelope.baseSellMultiplier())).setScale(2, RoundingMode.HALF_UP);
    }

    private String toRuntimePolicy(final CatalogPolicy policy) {
        return switch (policy) {
            case ALWAYS_AVAILABLE -> "UNLIMITED_BUY";
            case EXCHANGE, SELL_ONLY -> "PLAYER_STOCKED";
            case DISABLED -> "DISABLED";
        };
    }

    private String defaultStockProfileName(final CatalogPolicy policy) {
        return switch (policy) {
            case ALWAYS_AVAILABLE -> "unlimited_buy_utility";
            case EXCHANGE -> "exchange_default";
            case SELL_ONLY -> "sell_only_cleanup";
            case DISABLED -> "disabled_placeholder";
        };
    }

    private String defaultEcoEnvelopeName(final CatalogPolicy policy) {
        return switch (policy) {
            case ALWAYS_AVAILABLE -> "world_damage_unlimited_buy";
            case EXCHANGE -> "exchange_default";
            case SELL_ONLY -> "sell_only_cleanup";
            case DISABLED -> "disabled_placeholder";
        };
    }

    private String buildDisplayName(final Material material) {
        final String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            final String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String joinNotes(final String note, final ItemFacts facts) {
        final List<String> notes = new ArrayList<>(4);
        if (note != null && !note.isBlank()) {
            notes.add(note);
        }
        if (facts.isBlock()) {
            notes.add("block-item");
        }
        if (facts.edible()) {
            notes.add("edible");
        }
        if (facts.maxStackSize() == 1) {
            notes.add("unstackable");
        }
        if (facts.fuelCandidate()) {
            notes.add("fuel");
        }
        return String.join(", ", notes);
    }

    private static String decimal(final BigDecimal value) {
        if (value == null) {
            return null;
        }
        return new DecimalFormat("0.00").format(value);
    }

    private static List<String> normalizeKeys(final List<String> keys) {
        return keys.stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
    }

    private static List<CatalogCategory> parseCategories(final List<String> values) {
        final List<CatalogCategory> categories = new ArrayList<>(values.size());
        for (final String value : values) {
            final CatalogCategory category = parseCategory(value);
            if (category != null) {
                categories.add(category);
            }
        }
        return categories;
    }

    private static CatalogCategory parseCategory(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CatalogCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<DerivationReason> parseDerivationReasons(final List<String> values) {
        final List<DerivationReason> reasons = new ArrayList<>(values.size());
        for (final String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                reasons.add(DerivationReason.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return reasons;
    }

    private static CatalogPolicy parsePolicy(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CatalogPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private ConfigurationSection asSection(final Map<?, ?> values) {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<?, ?> entry : values.entrySet()) {
            yaml.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return yaml;
    }

    private record LiveSnapshotEntry(
        String itemKey,
        String category,
        String policy,
        boolean buyEnabled,
        boolean sellEnabled,
        int stockCap,
        BigDecimal buyPrice,
        BigDecimal sellPrice
    ) {
        boolean matches(final AdminCatalogPlanEntry entry) {
            return this.category.equals(entry.category().name())
                && this.policy.equals(entry.runtimePolicy())
                && this.buyEnabled == entry.buyEnabled()
                && this.sellEnabled == entry.sellEnabled()
                && this.stockCap == entry.stockCap()
                && this.buyPrice.compareTo(entry.buyPrice()) == 0
                && this.sellPrice.compareTo(entry.sellPrice()) == 0;
        }

        String describeChange(final AdminCatalogPlanEntry entry) {
            final List<String> changes = new ArrayList<>();
            if (!this.category.equals(entry.category().name())) {
                changes.add("category " + this.category + " -> " + entry.category().name());
            }
            if (!this.policy.equals(entry.runtimePolicy())) {
                changes.add("policy " + this.policy + " -> " + entry.runtimePolicy());
            }
            if (this.buyEnabled != entry.buyEnabled()) {
                changes.add("buy-enabled " + this.buyEnabled + " -> " + entry.buyEnabled());
            }
            if (this.sellEnabled != entry.sellEnabled()) {
                changes.add("sell-enabled " + this.sellEnabled + " -> " + entry.sellEnabled());
            }
            if (this.stockCap != entry.stockCap()) {
                changes.add("stock-cap " + this.stockCap + " -> " + entry.stockCap());
            }
            if (this.buyPrice.compareTo(entry.buyPrice()) != 0) {
                changes.add("buy-price " + this.buyPrice + " -> " + entry.buyPrice());
            }
            if (this.sellPrice.compareTo(entry.sellPrice()) != 0) {
                changes.add("sell-price " + this.sellPrice + " -> " + entry.sellPrice());
            }
            return String.join("; ", changes);
        }
    }
}
