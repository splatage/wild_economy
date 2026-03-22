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
        this.requireManagedFile(
            rootValuesFile,
            "root-values.yml",
            "Run /shopadmin reload to regenerate bundled defaults, then review root-values.yml before rerunning this action."
        );

        final RootValueLoader rootValues = RootValueLoader.fromFile(rootValuesFile);
        final RecipeGraph recipeGraph = new BukkitRecipeGraphBuilder().build();
        final RootAnchoredDerivationService derivationService = new RootAnchoredDerivationService(recipeGraph, rootValues);
        final BukkitMaterialScanner materialScanner = new BukkitMaterialScanner(rootValues);
        final DefaultCategoryClassifier classifier = new DefaultCategoryClassifier();
        final DefaultPolicySuggestionService basePolicyService = new DefaultPolicySuggestionService(DEFAULT_MAX_AUTO_INCLUSION_DEPTH);

        final List<AdminCatalogPolicyRule> rules = this.loadPolicyRules(new File(dataFolder, "policy-rules.yml"));
        final Map<CatalogPolicy, AdminCatalogPolicyProfile> policyProfiles = AdminCatalogPolicyProfileLoader.load(new File(dataFolder, "policy-profiles.yml"));
        final Map<String, Integer> ruleMatchCounts = new LinkedHashMap<>();
        final Map<String, Integer> ruleApplicationCounts = new LinkedHashMap<>();
        for (final AdminCatalogPolicyRule rule : rules) {
            ruleMatchCounts.put(rule.id(), 0);
            ruleApplicationCounts.put(rule.id(), 0);
        }
        final Map<String, AdminCatalogManualOverride> overrides = this.loadManualOverrides(new File(dataFolder, "manual-overrides.yml"));
        final Map<String, AdminCatalogStockProfile> stockProfiles = this.loadStockProfiles(new File(dataFolder, "stock-profiles.yml"));
        final Map<String, AdminCatalogEcoEnvelope> ecoEnvelopes = this.loadEcoEnvelopes(new File(dataFolder, "eco-envelopes.yml"));

        final List<AdminCatalogPlanEntry> proposedEntries = new ArrayList<>();
        final List<AdminCatalogDecisionTrace> decisionTraces = new ArrayList<>();
        final List<AdminCatalogValidationIssue> validationIssues = new ArrayList<>();
        final Map<DerivationReason, Integer> forcedDisableCountsByReason = new EnumMap<>(DerivationReason.class);

        int disabledCount = 0;
        int unresolvedCount = 0;

        for (final ItemFacts facts : materialScanner.scanAll()) {
            final CatalogCategory baseCategory = classifier.classify(facts);
            final DerivedItemResult derivation = derivationService.resolve(facts.key());
            final CatalogPolicy baseSuggestedPolicy = basePolicyService.suggest(facts, baseCategory, derivation);
            CatalogPolicy policy = baseSuggestedPolicy;
            CatalogCategory finalCategory = baseCategory;

            final String canonicalItemKey = AdminCatalogItemKeys.canonicalize(facts.key());
            final List<String> matchedRuleIds = new ArrayList<>();
            final List<AdminCatalogPolicyRule> fallbackRules = new ArrayList<>();
            String winningRuleId = null;
            AdminCatalogPolicyRule winningRule = null;
            String stockProfileName = this.defaultStockProfileName(policy, policyProfiles);
            String ecoEnvelopeName = this.defaultEcoEnvelopeName(policy, policyProfiles);
            boolean stockProfileExplicit = false;
            boolean ecoEnvelopeExplicit = false;
            String postRuleAdjustment = null;
            String note = null;

            for (final AdminCatalogPolicyRule rule : rules) {
                if (!rule.hasMatchCriteria()) {
                    if (this.ruleHasConfiguredOutputs(rule)) {
                        fallbackRules.add(rule);
                    }
                    continue;
                }
                if (!rule.matches(facts, finalCategory, derivation)) {
                    continue;
                }
                ruleMatchCounts.computeIfPresent(rule.id(), (ignored, value) -> Integer.valueOf(value.intValue() + 1));
                matchedRuleIds.add(rule.id());

                if (this.shouldPreserveWinningRule(winningRule, policy, rule)) {
                    continue;
                }

                final RuleApplication applied = this.applyRule(
                    rule,
                    policy,
                    stockProfileName,
                    stockProfileExplicit,
                    ecoEnvelopeName,
                    ecoEnvelopeExplicit,
                    note,
                    policyProfiles
                );
                policy = applied.policy();
                stockProfileName = applied.stockProfileName();
                stockProfileExplicit = applied.stockProfileExplicit();
                ecoEnvelopeName = applied.ecoEnvelopeName();
                ecoEnvelopeExplicit = applied.ecoEnvelopeExplicit();
                note = applied.note();
                winningRuleId = rule.id();
                winningRule = rule;
            }

            if (winningRuleId == null) {
                for (final AdminCatalogPolicyRule rule : fallbackRules) {
                    final RuleApplication applied = this.applyRule(
                        rule,
                        policy,
                        stockProfileName,
                        stockProfileExplicit,
                        ecoEnvelopeName,
                        ecoEnvelopeExplicit,
                        note,
                        policyProfiles
                    );
                    policy = applied.policy();
                    stockProfileName = applied.stockProfileName();
                    stockProfileExplicit = applied.stockProfileExplicit();
                    ecoEnvelopeName = applied.ecoEnvelopeName();
                    ecoEnvelopeExplicit = applied.ecoEnvelopeExplicit();
                    note = applied.note();
                    winningRuleId = rule.id();
                    winningRule = rule;
                }
            }

            if (winningRuleId != null) {
                ruleApplicationCounts.computeIfPresent(winningRuleId, (ignored, value) -> Integer.valueOf(value.intValue() + 1));
            }

            final AdminCatalogManualOverride override = overrides.get(canonicalItemKey);
            if (override != null) {
                if (override.policy() != null) {
                    policy = override.policy();
                    if (!this.hasText(override.stockProfile())) {
                        stockProfileName = this.defaultStockProfileName(policy, policyProfiles);
                        stockProfileExplicit = false;
                    }
                    if (!this.hasText(override.ecoEnvelope())) {
                        ecoEnvelopeName = this.defaultEcoEnvelopeName(policy, policyProfiles);
                        ecoEnvelopeExplicit = false;
                    }
                }
                if (override.category() != null) {
                    finalCategory = override.category();
                }
                if (this.hasText(override.stockProfile())) {
                    stockProfileName = override.stockProfile();
                    stockProfileExplicit = true;
                }
                if (this.hasText(override.ecoEnvelope())) {
                    ecoEnvelopeName = override.ecoEnvelope();
                    ecoEnvelopeExplicit = true;
                }
                if (this.hasText(override.note())) {
                    note = override.note();
                }
            }

            if (policy != CatalogPolicy.DISABLED && !derivation.rootValuePresent() && derivation.derivedValue() == null) {
                policy = CatalogPolicy.DISABLED;
                stockProfileName = this.defaultStockProfileName(policy, policyProfiles);
                ecoEnvelopeName = this.defaultEcoEnvelopeName(policy, policyProfiles);
                stockProfileExplicit = false;
                ecoEnvelopeExplicit = false;
                postRuleAdjustment = "Forced to DISABLED because no rooted value path was resolved.";
                unresolvedCount++;
                forcedDisableCountsByReason.merge(derivation.reason(), Integer.valueOf(1), Integer::sum);
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

            final AdminCatalogPolicyProfile policyProfile = this.resolvePolicyProfile(policy, policyProfiles);
            if (policyProfile == null) {
                validationIssues.add(
                    new AdminCatalogValidationIssue(
                        AdminCatalogValidationIssue.Severity.ERROR,
                        facts.key() + " references missing policy profile for '" + policy.name() + "'."
                    )
                );
            }

            final BigDecimal anchorValue = this.resolveAnchorValue(derivation);
            final BigDecimal buyPrice = this.computeBuyPrice(anchorValue, ecoEnvelope);
            final BigDecimal sellPrice = this.computeSellPrice(anchorValue, ecoEnvelope);

            final String runtimePolicy = policyProfile == null ? this.toRuntimePolicy(policy) : policyProfile.runtimePolicy();
            final boolean buyEnabled = policyProfile != null && policyProfile.buyEnabled();
            final boolean sellEnabled = policyProfile != null && policyProfile.sellEnabled();
            final boolean stockBacked = policyProfile != null && policyProfile.stockBacked();
            final boolean unlimitedBuy = policyProfile != null && policyProfile.unlimitedBuy();
            final boolean requiresPlayerStockToBuy = policyProfile != null && policyProfile.requiresPlayerStockToBuy();

            final AdminCatalogPlanEntry planEntry = new AdminCatalogPlanEntry(
                facts.key(),
                this.buildDisplayName(facts.material()),
                finalCategory,
                policy,
                policyProfile == null ? policy.name() : policyProfile.id(),
                runtimePolicy,
                buyEnabled,
                sellEnabled,
                stockBacked,
                unlimitedBuy,
                requiresPlayerStockToBuy,
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
                    baseSuggestedPolicy,
                    matchedRuleIds,
                    winningRuleId,
                    override != null,
                    policy,
                    finalCategory,
                    stockProfileName,
                    ecoEnvelopeName,
                    postRuleAdjustment,
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
        this.requireManagedFile(
            liveCatalogFile,
            "exchange-items.yml",
            "Run /shopadmin reload to regenerate bundled defaults, then review exchange-items.yml before rerunning this action."
        );
        final Map<String, LiveSnapshotEntry> currentLive = this.loadLiveSnapshot(liveCatalogFile);
        final List<AdminCatalogPlanEntry> liveEntries = proposedEntries.stream()
            .filter(entry -> entry.policy() != CatalogPolicy.DISABLED)
            .toList();
        final List<AdminCatalogDiffEntry> diffEntries = this.diff(currentLive, liveEntries);

        this.addCatalogWarnings(
            validationIssues,
            rules,
            ruleMatchCounts,
            proposedEntries,
            disabledCount,
            forcedDisableCountsByReason
        );

        this.writeGeneratedOutputs(
            generatedDirectory,
            rules,
            proposedEntries,
            liveEntries,
            decisionTraces,
            diffEntries,
            validationIssues,
            ruleMatchCounts,
            ruleApplicationCounts
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

    private List<AdminCatalogPolicyRule> loadPolicyRules(final File file) throws IOException {
        this.requireManagedFile(
            file,
            "policy-rules.yml",
            "Run /shopadmin reload to regenerate bundled defaults, then review policy-rules.yml before rerunning this action."
        );
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final List<Map<?, ?>> rawRules = yaml.getMapList("rules");
        final List<AdminCatalogPolicyRule> rules = new ArrayList<>(rawRules.size());

        int syntheticIndex = 1;
        for (final Map<?, ?> rawRule : rawRules) {
            final Map<?, ?> match = this.getMap(rawRule, "match");
            final Map<?, ?> set = this.getMap(rawRule, "set");

            rules.add(
                new AdminCatalogPolicyRule(
                    this.getString(rawRule, "id", "rule-" + syntheticIndex++),
                    normalizeKeys(this.getStringList(match, "item-keys")),
                    normalizeKeys(this.getStringList(match, "item-key-patterns")),
                    parseCategories(this.getStringList(match, "categories")),
                    parseDerivationReasons(this.getStringList(match, "derivation-reasons")),
                    this.getInteger(match, "min-derivation-depth"),
                    this.getInteger(match, "max-derivation-depth"),
                    this.getBoolean(match, "root-value-present"),
                    parsePolicy(this.getString(set, "policy", null)),
                    this.getString(set, "stock-profile", null),
                    this.getString(set, "eco-envelope", null),
                    this.getString(set, "note", null)
                )
            );
        }
        return rules;
    }

    private Map<String, AdminCatalogManualOverride> loadManualOverrides(final File file) throws IOException {
        this.requireManagedFile(
            file,
            "manual-overrides.yml",
            "Run /shopadmin reload to regenerate bundled defaults, then review manual-overrides.yml before rerunning this action."
        );
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
            final String normalizedItemKey = AdminCatalogItemKeys.canonicalize(itemKey);
            overrides.put(
                normalizedItemKey,
                new AdminCatalogManualOverride(
                    normalizedItemKey,
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

    private Map<String, AdminCatalogStockProfile> loadStockProfiles(final File file) throws IOException {
        this.requireManagedFile(
            file,
            "stock-profiles.yml",
            "Run /shopadmin reload to regenerate bundled defaults, then review stock-profiles.yml before rerunning this action."
        );
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

    private Map<String, AdminCatalogEcoEnvelope> loadEcoEnvelopes(final File file) throws IOException {
        this.requireManagedFile(
            file,
            "eco-envelopes.yml",
            "Run /shopadmin reload to regenerate bundled defaults, then review eco-envelopes.yml before rerunning this action."
        );
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

    private void requireManagedFile(
        final File file,
        final String resourceName,
        final String nextStep
    ) throws IOException {
        if (file.isFile()) {
            return;
        }
        throw new IOException(
            "Required config file '"
                + resourceName
                + "' is missing at "
                + file.getAbsolutePath()
                + ". "
                + nextStep
        );
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
            final String normalizedItemKey = AdminCatalogItemKeys.canonicalize(itemKey);
            snapshot.put(
                normalizedItemKey,
                new LiveSnapshotEntry(
                    normalizedItemKey,
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
        final List<AdminCatalogPolicyRule> rules,
        final List<AdminCatalogPlanEntry> proposedEntries,
        final List<AdminCatalogPlanEntry> liveEntries,
        final List<AdminCatalogDecisionTrace> decisionTraces,
        final List<AdminCatalogDiffEntry> diffEntries,
        final List<AdminCatalogValidationIssue> validationIssues,
        final Map<String, Integer> ruleMatchCounts,
        final Map<String, Integer> ruleApplicationCounts
    ) throws IOException {
        this.writeGeneratedCatalog(new File(generatedDirectory, "generated-catalog.yml"), proposedEntries);
        this.writeGeneratedSummary(
            new File(generatedDirectory, "generated-summary.yml"),
            proposedEntries,
            liveEntries,
            validationIssues,
            ruleMatchCounts,
            ruleApplicationCounts
        );
        this.writeGeneratedDiff(new File(generatedDirectory, "generated-diff.yml"), diffEntries);
        this.writeGeneratedValidation(new File(generatedDirectory, "generated-validation.yml"), validationIssues);
        this.writeDecisionTraces(new File(generatedDirectory, "item-decision-traces.yml"), decisionTraces);
        this.writeRuleImpacts(
            new File(generatedDirectory, "generated-rule-impacts.yml"),
            this.buildRuleImpacts(rules, ruleMatchCounts, ruleApplicationCounts, decisionTraces)
        );
        this.writeReviewBuckets(
            new File(generatedDirectory, "generated-review-buckets.yml"),
            this.buildReviewBuckets(proposedEntries, decisionTraces)
        );
    }

    private void writeGeneratedCatalog(final File file, final List<AdminCatalogPlanEntry> entries) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final AdminCatalogPlanEntry entry : entries) {
            final String base = "items." + entry.itemKey();
            yaml.set(base + ".display-name", entry.displayName());
            yaml.set(base + ".category", entry.category().name());
            yaml.set(base + ".admin-policy", entry.policy().name());
            yaml.set(base + ".policy-profile", entry.policyProfileId());
            yaml.set(base + ".runtime-policy", entry.runtimePolicy());
            yaml.set(base + ".stock-profile", entry.stockProfile());
            yaml.set(base + ".eco-envelope", entry.ecoEnvelope());
            yaml.set(base + ".buy-enabled", entry.buyEnabled());
            yaml.set(base + ".sell-enabled", entry.sellEnabled());
            yaml.set(base + ".stock-backed", entry.stockBacked());
            yaml.set(base + ".unlimited-buy", entry.unlimitedBuy());
            yaml.set(base + ".requires-player-stock-to-buy", entry.requiresPlayerStockToBuy());
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
        final List<AdminCatalogPlanEntry> liveEntries,
        final List<AdminCatalogValidationIssue> validationIssues,
        final Map<String, Integer> ruleMatchCounts,
        final Map<String, Integer> ruleApplicationCounts
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

        int unlimitedBuyCount = 0;
        int stockBackedCount = 0;
        int buyEnabledCount = 0;
        int sellEnabledCount = 0;
        for (final AdminCatalogPlanEntry entry : proposedEntries) {
            if (entry.unlimitedBuy()) {
                unlimitedBuyCount++;
            }
            if (entry.stockBacked()) {
                stockBackedCount++;
            }
            if (entry.buyEnabled()) {
                buyEnabledCount++;
            }
            if (entry.sellEnabled()) {
                sellEnabledCount++;
            }
        }
        yaml.set("counts.effective-behavior.unlimited-buy", unlimitedBuyCount);
        yaml.set("counts.effective-behavior.stock-backed", stockBackedCount);
        yaml.set("counts.effective-behavior.buy-enabled", buyEnabledCount);
        yaml.set("counts.effective-behavior.sell-enabled", sellEnabledCount);

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

        final Map<String, Integer> derivationReasonCounts = new LinkedHashMap<>();
        for (final AdminCatalogPlanEntry entry : proposedEntries) {
            derivationReasonCounts.compute(entry.derivationReason(), (ignored, value) -> value == null ? 1 : value + 1);
        }
        for (final Map.Entry<String, Integer> entry : derivationReasonCounts.entrySet()) {
            yaml.set("counts.derivation-reason." + entry.getKey(), entry.getValue());
        }

        final long warningCount = validationIssues.stream()
            .filter(issue -> issue.severity() == AdminCatalogValidationIssue.Severity.WARNING)
            .count();
        final long errorCount = validationIssues.stream()
            .filter(issue -> issue.severity() == AdminCatalogValidationIssue.Severity.ERROR)
            .count();
        yaml.set("counts.validation.warnings", warningCount);
        yaml.set("counts.validation.errors", errorCount);

        for (final Map.Entry<String, Integer> entry : ruleMatchCounts.entrySet()) {
            yaml.set("counts.rule-matches." + entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, Integer> entry : ruleApplicationCounts.entrySet()) {
            yaml.set("counts.rule-wins." + entry.getKey(), entry.getValue());
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
            yaml.set(base + ".post-rule-adjustment", trace.postRuleAdjustment());
            yaml.set(base + ".display-name", trace.displayName());
            yaml.set(base + ".note", trace.note());
        }
        yaml.save(file);
    }


    private List<AdminCatalogRuleImpact> buildRuleImpacts(
        final List<AdminCatalogPolicyRule> rules,
        final Map<String, Integer> ruleMatchCounts,
        final Map<String, Integer> ruleApplicationCounts,
        final List<AdminCatalogDecisionTrace> decisionTraces
    ) {
        final Map<String, List<String>> matchedSamples = new LinkedHashMap<>();
        final Map<String, List<String>> winningSamples = new LinkedHashMap<>();
        final Map<String, List<String>> lostSamples = new LinkedHashMap<>();
        final Map<String, Map<CatalogPolicy, Integer>> winningPolicies = new LinkedHashMap<>();
        final Map<String, Map<CatalogPolicy, Integer>> lostToPolicies = new LinkedHashMap<>();
        final Map<String, Map<String, Integer>> lostToRules = new LinkedHashMap<>();

        for (final AdminCatalogPolicyRule rule : rules) {
            matchedSamples.put(rule.id(), new ArrayList<>());
            winningSamples.put(rule.id(), new ArrayList<>());
            lostSamples.put(rule.id(), new ArrayList<>());
            winningPolicies.put(rule.id(), this.newPolicyCounterMap());
            lostToPolicies.put(rule.id(), this.newPolicyCounterMap());
            lostToRules.put(rule.id(), new LinkedHashMap<>());
        }

        for (final AdminCatalogDecisionTrace trace : decisionTraces) {
            for (final String matchedRuleId : trace.matchedRuleIds()) {
                this.addSample(matchedSamples.get(matchedRuleId), trace.itemKey());
                if (!matchedRuleId.equals(trace.winningRuleId())) {
                    this.addSample(lostSamples.get(matchedRuleId), trace.itemKey());

                    final Map<CatalogPolicy, Integer> losingPolicies = lostToPolicies.get(matchedRuleId);
                    if (losingPolicies != null) {
                        losingPolicies.compute(trace.finalPolicy(), (ignored, value) -> Integer.valueOf(value.intValue() + 1));
                    }

                    final Map<String, Integer> losingRules = lostToRules.get(matchedRuleId);
                    if (losingRules != null) {
                        final String losingTo = this.hasText(trace.winningRuleId())
                            ? trace.winningRuleId()
                            : (this.hasText(trace.postRuleAdjustment()) ? "post-rule-adjustment" : "unresolved-final-outcome");
                        losingRules.compute(losingTo, (ignored, value) -> Integer.valueOf(value == null ? 1 : value.intValue() + 1));
                    }
                }
            }
            if (this.hasText(trace.winningRuleId())) {
                this.addSample(winningSamples.get(trace.winningRuleId()), trace.itemKey());
                final Map<CatalogPolicy, Integer> counts = winningPolicies.get(trace.winningRuleId());
                if (counts != null) {
                    counts.compute(trace.finalPolicy(), (ignored, value) -> Integer.valueOf(value.intValue() + 1));
                }
            }
        }

        final List<AdminCatalogRuleImpact> impacts = new ArrayList<>(rules.size());
        for (final AdminCatalogPolicyRule rule : rules) {
            final int matchCount = ruleMatchCounts.getOrDefault(rule.id(), Integer.valueOf(0)).intValue();
            final int winCount = ruleApplicationCounts.getOrDefault(rule.id(), Integer.valueOf(0)).intValue();
            final int lossCount = Math.max(0, matchCount - winCount);
            impacts.add(
                new AdminCatalogRuleImpact(
                    rule.id(),
                    !rule.hasMatchCriteria(),
                    rule.hasMatchCriteria(),
                    matchCount,
                    winCount,
                    lossCount,
                    winningPolicies.get(rule.id()),
                    lostToPolicies.get(rule.id()),
                    lostToRules.get(rule.id()),
                    matchedSamples.get(rule.id()),
                    winningSamples.get(rule.id()),
                    lostSamples.get(rule.id())
                )
            );
        }
        return impacts;
    }



    private List<AdminCatalogReviewBucket> buildReviewBuckets(
        final List<AdminCatalogPlanEntry> proposedEntries,
        final List<AdminCatalogDecisionTrace> decisionTraces
    ) {
        final Map<String, AdminCatalogPlanEntry> entriesByKey = new LinkedHashMap<>();
        for (final AdminCatalogPlanEntry entry : proposedEntries) {
            entriesByKey.put(AdminCatalogItemKeys.canonicalize(entry.itemKey()), entry);
        }

        final Map<String, AdminCatalogReviewBucketBuilder> buckets = new LinkedHashMap<>();
        buckets.put(
            "live-misc-items",
            new AdminCatalogReviewBucketBuilder(
                "live-misc-items",
                "Live non-disabled items still classified into MISC and likely worth category review."
            )
        );
        buckets.put(
            "no-root-path",
            new AdminCatalogReviewBucketBuilder(
                "no-root-path",
                "Items blocked because no recipe path and no root anchor could be resolved."
            )
        );
        buckets.put(
            "blocked-paths",
            new AdminCatalogReviewBucketBuilder(
                "blocked-paths",
                "Items with a recipe family present but all rooted derivation paths blocked."
            )
        );
        buckets.put(
            "manual-overrides",
            new AdminCatalogReviewBucketBuilder(
                "manual-overrides",
                "Items currently relying on explicit manual overrides."
            )
        );
        buckets.put(
            "sell-only-review",
            new AdminCatalogReviewBucketBuilder(
                "sell-only-review",
                "Items assigned SELL_ONLY and worth admin review for long-term policy fit."
            )
        );

        for (final AdminCatalogPlanEntry entry : proposedEntries) {
            if (entry.policy() != CatalogPolicy.DISABLED && entry.category() == CatalogCategory.MISC) {
                buckets.get("live-misc-items").add(entry.itemKey(), this.classifyLiveMiscSubgroup(entry.itemKey()));
            }
            if (entry.policy() == CatalogPolicy.SELL_ONLY) {
                buckets.get("sell-only-review").add(entry.itemKey(), this.classifySellOnlySubgroup(entry.itemKey()));
            }
        }

        for (final AdminCatalogDecisionTrace trace : decisionTraces) {
            if (trace.derivationReason() == DerivationReason.NO_RECIPE_AND_NO_ROOT) {
                buckets.get("no-root-path").add(trace.itemKey(), this.classifyNoRootPathSubgroup(trace.itemKey(), trace));
            }
            if (trace.derivationReason() == DerivationReason.ALL_PATHS_BLOCKED) {
                buckets.get("blocked-paths").add(trace.itemKey(), this.classifyBlockedPathSubgroup(trace));
            }
            if (trace.manualOverrideApplied()) {
                buckets.get("manual-overrides").add(trace.itemKey(), this.classifyManualOverrideSubgroup(trace, entriesByKey));
            }
        }

        final List<AdminCatalogReviewBucket> reviewBuckets = new ArrayList<>();
        for (final AdminCatalogReviewBucketBuilder builder : buckets.values()) {
            reviewBuckets.add(builder.build());
        }
        return reviewBuckets;
    }



    private void writeRuleImpacts(final File file, final List<AdminCatalogRuleImpact> impacts) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final AdminCatalogRuleImpact impact : impacts) {
            final String base = "rules." + impact.ruleId();
            yaml.set(base + ".fallback-rule", impact.fallbackRule());
            yaml.set(base + ".has-match-criteria", impact.hasMatchCriteria());
            yaml.set(base + ".match-count", impact.matchCount());
            yaml.set(base + ".win-count", impact.winCount());
            yaml.set(base + ".loss-count", impact.lossCount());
            for (final Map.Entry<CatalogPolicy, Integer> entry : impact.winningPolicies().entrySet()) {
                yaml.set(base + ".winning-policies." + entry.getKey().name(), entry.getValue());
            }
            for (final Map.Entry<CatalogPolicy, Integer> entry : impact.lostToPolicies().entrySet()) {
                yaml.set(base + ".lost-to-policies." + entry.getKey().name(), entry.getValue());
            }
            for (final Map.Entry<String, Integer> entry : impact.lostToRules().entrySet()) {
                yaml.set(base + ".lost-to-rules." + entry.getKey(), entry.getValue());
            }
            yaml.set(base + ".sample-matched-items", impact.sampleMatchedItems());
            yaml.set(base + ".sample-winning-items", impact.sampleWinningItems());
            yaml.set(base + ".sample-lost-items", impact.sampleLostItems());
        }
        yaml.save(file);
    }



    private void writeReviewBuckets(final File file, final List<AdminCatalogReviewBucket> reviewBuckets) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final AdminCatalogReviewBucket bucket : reviewBuckets) {
            final String base = "buckets." + bucket.bucketId();
            yaml.set(base + ".description", bucket.description());
            yaml.set(base + ".count", bucket.count());
            yaml.set(base + ".sample-items", bucket.sampleItems());
            for (final Map.Entry<String, Integer> entry : bucket.subgroupCounts().entrySet()) {
                yaml.set(base + ".subgroup-counts." + entry.getKey(), entry.getValue());
            }
            for (final Map.Entry<String, List<String>> entry : bucket.subgroupSampleItems().entrySet()) {
                yaml.set(base + ".subgroup-sample-items." + entry.getKey(), entry.getValue());
            }
        }
        yaml.save(file);
    }


    private void addSample(final List<String> samples, final String itemKey) {
        if (samples == null || !this.hasText(itemKey)) {
            return;
        }
        if (samples.size() >= 8 || samples.contains(itemKey)) {
            return;
        }
        samples.add(itemKey);
    }

    private Map<CatalogPolicy, Integer> newPolicyCounterMap() {
        final Map<CatalogPolicy, Integer> counts = new EnumMap<>(CatalogPolicy.class);
        for (final CatalogPolicy policy : CatalogPolicy.values()) {
            counts.put(policy, Integer.valueOf(0));
        }
        return counts;
    }

    private String classifyLiveMiscSubgroup(final String itemKey) {
        final String key = AdminCatalogItemKeys.canonicalize(itemKey);
        if (key.contains("copper") || key.endsWith("_bars") || key.endsWith("_chain")) {
            return "metal-building";
        }
        if (key.endsWith("_table") || key.endsWith("_torch") || key.endsWith("_lantern") || key.endsWith("_rod")) {
            return "utility-workstations";
        }
        if (key.contains("ice") || key.contains("bone") || key.contains("coral")) {
            return "world-materials";
        }
        if (key.endsWith("_door") || key.endsWith("_trapdoor") || key.endsWith("_grate")) {
            return "building-hardware";
        }
        return "misc-review";
    }

    private String classifyNoRootPathSubgroup(final String itemKey, final AdminCatalogDecisionTrace trace) {
        final String key = AdminCatalogItemKeys.canonicalize(itemKey);
        if (key.endsWith("_spawn_egg")) {
            return "spawn-eggs";
        }
        if (key.endsWith("_sapling") || key.endsWith("_leaves") || key.endsWith("_flower") || key.contains("pottery_sherd")) {
            return "natural-or-loot";
        }
        if (key.contains("_shelf") || key.endsWith("_anvil") || key.endsWith("_cluster")) {
            return "crafted-or-utility";
        }
        if (trace.classifiedCategory() != null) {
            return "category-" + trace.classifiedCategory().name().toLowerCase(Locale.ROOT);
        }
        return "other";
    }

    private String classifyBlockedPathSubgroup(final AdminCatalogDecisionTrace trace) {
        if (trace.classifiedCategory() != null) {
            return "category-" + trace.classifiedCategory().name().toLowerCase(Locale.ROOT);
        }
        return "other";
    }

    private String classifyManualOverrideSubgroup(
        final AdminCatalogDecisionTrace trace,
        final Map<String, AdminCatalogPlanEntry> entriesByKey
    ) {
        final AdminCatalogPlanEntry entry = entriesByKey.get(AdminCatalogItemKeys.canonicalize(trace.itemKey()));
        if (entry != null && entry.policy() != trace.finalPolicy()) {
            return "policy-override";
        }
        return "manual-override";
    }

    private String classifySellOnlySubgroup(final String itemKey) {
        final String key = AdminCatalogItemKeys.canonicalize(itemKey);
        if (key.endsWith("_sword")) {
            return "swords";
        }
        if (key.endsWith("_shovel")) {
            return "shovels";
        }
        if (key.endsWith("_pickaxe")) {
            return "pickaxes";
        }
        if (key.endsWith("_axe")) {
            return "axes";
        }
        if (key.endsWith("_hoe")) {
            return "hoes";
        }
        if (key.endsWith("_helmet") || key.endsWith("_chestplate") || key.endsWith("_leggings") || key.endsWith("_boots")) {
            return "armor";
        }
        return "other";
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
            yaml.set(base + ".admin-policy", entry.policy().name());
            yaml.set(base + ".policy-profile", entry.policyProfileId());
            yaml.set(base + ".policy", entry.runtimePolicy());
            yaml.set(base + ".buy-enabled", entry.buyEnabled());
            yaml.set(base + ".sell-enabled", entry.sellEnabled());
            yaml.set(base + ".stock-backed", entry.stockBacked());
            yaml.set(base + ".unlimited-buy", entry.unlimitedBuy());
            yaml.set(base + ".requires-player-stock-to-buy", entry.requiresPlayerStockToBuy());
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
            "policy-profiles.yml",
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

    private AdminCatalogPolicyProfile resolvePolicyProfile(
        final CatalogPolicy policy,
        final Map<CatalogPolicy, AdminCatalogPolicyProfile> policyProfiles
    ) {
        return policyProfiles.get(policy);
    }

    private String defaultStockProfileName(
        final CatalogPolicy policy,
        final Map<CatalogPolicy, AdminCatalogPolicyProfile> policyProfiles
    ) {
        final AdminCatalogPolicyProfile profile = this.resolvePolicyProfile(policy, policyProfiles);
        if (profile != null && this.hasText(profile.defaultStockProfile())) {
            return profile.defaultStockProfile();
        }
        return switch (policy) {
            case ALWAYS_AVAILABLE -> "unlimited_buy_utility";
            case EXCHANGE -> "exchange_default";
            case SELL_ONLY -> "sell_only_cleanup";
            case DISABLED -> "disabled_placeholder";
        };
    }

    private String defaultEcoEnvelopeName(
        final CatalogPolicy policy,
        final Map<CatalogPolicy, AdminCatalogPolicyProfile> policyProfiles
    ) {
        final AdminCatalogPolicyProfile profile = this.resolvePolicyProfile(policy, policyProfiles);
        if (profile != null && this.hasText(profile.defaultEcoEnvelope())) {
            return profile.defaultEcoEnvelope();
        }
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

    private boolean ruleHasConfiguredOutputs(final AdminCatalogPolicyRule rule) {
        return rule.policy() != null
            || this.hasText(rule.stockProfile())
            || this.hasText(rule.ecoEnvelope())
            || this.hasText(rule.note());
    }

    private boolean shouldPreserveWinningRule(
        final AdminCatalogPolicyRule currentWinningRule,
        final CatalogPolicy currentPolicy,
        final AdminCatalogPolicyRule candidateRule
    ) {
        if (currentWinningRule == null) {
            return false;
        }
        if (currentPolicy != CatalogPolicy.DISABLED || candidateRule.policy() != CatalogPolicy.DISABLED) {
            return false;
        }
        return this.isBroadDerivationDisableRule(candidateRule) && !this.isBroadDerivationDisableRule(currentWinningRule);
    }

    private boolean isBroadDerivationDisableRule(final AdminCatalogPolicyRule rule) {
        return rule.policy() == CatalogPolicy.DISABLED
            && !rule.derivationReasons().isEmpty()
            && rule.itemKeys().isEmpty()
            && rule.itemKeyPatterns().isEmpty()
            && rule.categories().isEmpty()
            && rule.minDerivationDepth() == null
            && rule.maxDerivationDepth() == null
            && rule.rootValuePresent() == null;
    }

    private RuleApplication applyRule(
        final AdminCatalogPolicyRule rule,
        final CatalogPolicy currentPolicy,
        final String currentStockProfileName,
        final boolean currentStockProfileExplicit,
        final String currentEcoEnvelopeName,
        final boolean currentEcoEnvelopeExplicit,
        final String currentNote,
        final Map<CatalogPolicy, AdminCatalogPolicyProfile> policyProfiles
    ) {
        CatalogPolicy policy = currentPolicy;
        String stockProfileName = currentStockProfileName;
        boolean stockProfileExplicit = currentStockProfileExplicit;
        String ecoEnvelopeName = currentEcoEnvelopeName;
        boolean ecoEnvelopeExplicit = currentEcoEnvelopeExplicit;
        String note = currentNote;

        if (rule.policy() != null) {
            policy = rule.policy();
            if (!stockProfileExplicit) {
                stockProfileName = this.defaultStockProfileName(policy, policyProfiles);
            }
            if (!ecoEnvelopeExplicit) {
                ecoEnvelopeName = this.defaultEcoEnvelopeName(policy, policyProfiles);
            }
        }
        if (this.hasText(rule.stockProfile())) {
            stockProfileName = rule.stockProfile();
            stockProfileExplicit = true;
        }
        if (this.hasText(rule.ecoEnvelope())) {
            ecoEnvelopeName = rule.ecoEnvelope();
            ecoEnvelopeExplicit = true;
        }
        if (this.hasText(rule.note())) {
            note = rule.note();
        }

        return new RuleApplication(
            policy,
            stockProfileName,
            stockProfileExplicit,
            ecoEnvelopeName,
            ecoEnvelopeExplicit,
            note
        );
    }

    private static String decimal(final BigDecimal value) {
        if (value == null) {
            return null;
        }
        return new DecimalFormat("0.00").format(value);
    }

    private static List<String> normalizeKeys(final List<String> keys) {
        return AdminCatalogItemKeys.canonicalizeAll(keys);
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

    private void addCatalogWarnings(
        final List<AdminCatalogValidationIssue> validationIssues,
        final List<AdminCatalogPolicyRule> rules,
        final Map<String, Integer> ruleMatchCounts,
        final List<AdminCatalogPlanEntry> proposedEntries,
        final int disabledCount,
        final Map<DerivationReason, Integer> forcedDisableCountsByReason
    ) {
        for (final AdminCatalogPolicyRule rule : rules) {
            if (!rule.hasMatchCriteria()) {
                continue;
            }
            final Integer matchCount = ruleMatchCounts.get(rule.id());
            if (matchCount != null && matchCount.intValue() == 0) {
                validationIssues.add(
                    new AdminCatalogValidationIssue(
                        AdminCatalogValidationIssue.Severity.WARNING,
                        "Policy rule '" + rule.id() + "' did not match any items."
                    )
                );
            }
        }

        final long sellOnlyCount = proposedEntries.stream()
            .filter(entry -> entry.policy() == CatalogPolicy.SELL_ONLY)
            .count();
        final boolean hasSellOnlyRule = rules.stream().anyMatch(rule -> rule.policy() == CatalogPolicy.SELL_ONLY);
        if (hasSellOnlyRule && sellOnlyCount == 0L) {
            validationIssues.add(
                new AdminCatalogValidationIssue(
                    AdminCatalogValidationIssue.Severity.WARNING,
                    "SELL_ONLY policy count is zero even though at least one rule targets SELL_ONLY."
                )
            );
        }

        for (final Map.Entry<DerivationReason, Integer> entry : forcedDisableCountsByReason.entrySet()) {
            validationIssues.add(
                new AdminCatalogValidationIssue(
                    AdminCatalogValidationIssue.Severity.WARNING,
                    entry.getValue() + " items were forced to DISABLED because no rooted value path was resolved (reason: "
                        + entry.getKey().name() + ")."
                )
            );
        }

        if (!proposedEntries.isEmpty()) {
            final double disabledRatio = (double) disabledCount / (double) proposedEntries.size();
            if (disabledRatio >= 0.85D) {
                validationIssues.add(
                    new AdminCatalogValidationIssue(
                        AdminCatalogValidationIssue.Severity.WARNING,
                        "Disabled item ratio is high at " + percentage(disabledRatio) + ". Review rule resolution and derivation coverage."
                    )
                );
            }

            final long noRootPathCount = proposedEntries.stream()
                .filter(entry -> "NO_RECIPE_AND_NO_ROOT".equals(entry.derivationReason()))
                .count();
            if (noRootPathCount >= Math.max(100L, proposedEntries.size() / 3L)) {
                validationIssues.add(
                    new AdminCatalogValidationIssue(
                        AdminCatalogValidationIssue.Severity.WARNING,
                        noRootPathCount + " items resolved to NO_RECIPE_AND_NO_ROOT. Review recipe coverage and root anchors."
                    )
                );
            }
        }
    }

    private Map<?, ?> getMap(final Map<?, ?> source, final String key) {
        if (source == null) {
            return Map.of();
        }
        final Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private List<String> getStringList(final Map<?, ?> source, final String key) {
        if (source == null) {
            return List.of();
        }
        final Object value = source.get(key);
        if (value instanceof List<?> list) {
            final List<String> strings = new ArrayList<>(list.size());
            for (final Object element : list) {
                if (element != null) {
                    strings.add(String.valueOf(element));
                }
            }
            return strings;
        }
        return List.of();
    }

    private String getString(final Map<?, ?> source, final String key, final String defaultValue) {
        if (source == null) {
            return defaultValue;
        }
        final Object value = source.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Integer getInteger(final Map<?, ?> source, final String key) {
        if (source == null) {
            return null;
        }
        final Object value = source.get(key);
        if (value instanceof Number number) {
            return Integer.valueOf(number.intValue());
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.valueOf(Integer.parseInt(stringValue.trim()));
            } catch (final NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean getBoolean(final Map<?, ?> source, final String key) {
        if (source == null) {
            return null;
        }
        final Object value = source.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Boolean.valueOf(Boolean.parseBoolean(stringValue.trim()));
        }
        return null;
    }

    private boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static String percentage(final double value) {
        return new DecimalFormat("0.0%").format(value);
    }

    private ConfigurationSection asSection(final Map<?, ?> values) {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<?, ?> entry : values.entrySet()) {
            yaml.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return yaml;
    }



    private static final class AdminCatalogReviewBucketBuilder {

        private final String bucketId;
        private final String description;
        private final List<String> sampleItems = new ArrayList<>();
        private final Map<String, Integer> subgroupCounts = new LinkedHashMap<>();
        private final Map<String, List<String>> subgroupSampleItems = new LinkedHashMap<>();
        private int count;

        private AdminCatalogReviewBucketBuilder(final String bucketId, final String description) {
            this.bucketId = bucketId;
            this.description = description;
        }

        private void add(final String itemKey, final String subgroup) {
            this.count++;
            if (this.sampleItems.size() < 12 && !this.sampleItems.contains(itemKey)) {
                this.sampleItems.add(itemKey);
            }
            if (subgroup == null || subgroup.isBlank()) {
                return;
            }
            this.subgroupCounts.compute(subgroup, (ignored, value) -> Integer.valueOf(value == null ? 1 : value.intValue() + 1));
            final List<String> samples = this.subgroupSampleItems.computeIfAbsent(subgroup, ignored -> new ArrayList<>());
            if (samples.size() >= 6 || samples.contains(itemKey)) {
                return;
            }
            samples.add(itemKey);
        }

        private AdminCatalogReviewBucket build() {
            return new AdminCatalogReviewBucket(
                this.bucketId,
                this.description,
                this.count,
                this.sampleItems,
                this.subgroupCounts,
                this.subgroupSampleItems
            );
        }
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

    private record RuleApplication(
        CatalogPolicy policy,
        String stockProfileName,
        boolean stockProfileExplicit,
        String ecoEnvelopeName,
        boolean ecoEnvelopeExplicit,
        String note
    ) {
    }
}
