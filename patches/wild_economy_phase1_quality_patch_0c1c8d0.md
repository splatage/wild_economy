# wild_economy Phase 1 quality patch (`0c1c8d071dbb21c7f0da372c904768845b0c755e`)
This bundle contains complete replacement files for the next Phase 1 admin/catalog quality pass.

---

## File: `src/main/java/com/splatage/wild_economy/catalog/admin/AdminCatalogItemKeys.java`

```java
package com.splatage.wild_economy.catalog.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminCatalogItemKeys {

    private static final String MINECRAFT_PREFIX = "minecraft:";

    private AdminCatalogItemKeys() {
    }

    public static String canonicalize(final String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(MINECRAFT_PREFIX)) {
            normalized = normalized.substring(MINECRAFT_PREFIX.length());
        }
        return normalized;
    }

    public static List<String> canonicalizeAll(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        final List<String> normalized = new ArrayList<>(values.size());
        for (final String value : values) {
            final String canonical = canonicalize(value);
            if (!canonical.isBlank()) {
                normalized.add(canonical);
            }
        }
        return List.copyOf(normalized);
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/admin/AdminCatalogPolicyRule.java`

```java
package com.splatage.wild_economy.catalog.admin;

import com.splatage.wild_economy.catalog.derive.DerivationReason;
import com.splatage.wild_economy.catalog.derive.DerivedItemResult;
import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.CatalogPolicy;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.List;
import java.util.Objects;

public record AdminCatalogPolicyRule(
    String id,
    List<String> itemKeys,
    List<String> itemKeyPatterns,
    List<CatalogCategory> categories,
    List<DerivationReason> derivationReasons,
    Integer minDerivationDepth,
    Integer maxDerivationDepth,
    Boolean rootValuePresent,
    CatalogPolicy policy,
    String stockProfile,
    String ecoEnvelope,
    String note
) {

    public AdminCatalogPolicyRule {
        Objects.requireNonNull(id, "id");
        itemKeys = AdminCatalogItemKeys.canonicalizeAll(itemKeys);
        itemKeyPatterns = AdminCatalogItemKeys.canonicalizeAll(itemKeyPatterns);
        categories = List.copyOf(categories);
        derivationReasons = List.copyOf(derivationReasons);
    }

    public boolean matches(
        final ItemFacts facts,
        final CatalogCategory category,
        final DerivedItemResult derivation
    ) {
        final String itemKey = AdminCatalogItemKeys.canonicalize(facts.key());

        if (!this.itemKeys.isEmpty() && !this.itemKeys.contains(itemKey)) {
            return false;
        }

        if (!this.itemKeyPatterns.isEmpty()) {
            boolean matchedPattern = false;
            for (final String pattern : this.itemKeyPatterns) {
                if (wildcardMatches(pattern, itemKey)) {
                    matchedPattern = true;
                    break;
                }
            }
            if (!matchedPattern) {
                return false;
            }
        }

        if (!this.categories.isEmpty() && !this.categories.contains(category)) {
            return false;
        }

        if (!this.derivationReasons.isEmpty() && !this.derivationReasons.contains(derivation.reason())) {
            return false;
        }

        if (this.rootValuePresent != null && this.rootValuePresent.booleanValue() != derivation.rootValuePresent()) {
            return false;
        }

        if (this.minDerivationDepth != null) {
            final int depth = derivation.derivationDepth() == null ? -1 : derivation.derivationDepth().intValue();
            if (depth < this.minDerivationDepth.intValue()) {
                return false;
            }
        }

        if (this.maxDerivationDepth != null) {
            final int depth = derivation.derivationDepth() == null ? Integer.MAX_VALUE : derivation.derivationDepth().intValue();
            if (depth > this.maxDerivationDepth.intValue()) {
                return false;
            }
        }

        return true;
    }

    public boolean hasMatchCriteria() {
        return !this.itemKeys.isEmpty()
            || !this.itemKeyPatterns.isEmpty()
            || !this.categories.isEmpty()
            || !this.derivationReasons.isEmpty()
            || this.minDerivationDepth != null
            || this.maxDerivationDepth != null
            || this.rootValuePresent != null;
    }

    private static boolean wildcardMatches(final String wildcardPattern, final String value) {
        final StringBuilder regex = new StringBuilder("^");
        for (final char c : wildcardPattern.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '$', '^', '+', '|' -> regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/admin/AdminCatalogPhaseOneService.java`

```java
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
        final Map<String, Integer> ruleMatchCounts = new LinkedHashMap<>();
        for (final AdminCatalogPolicyRule rule : rules) {
            ruleMatchCounts.put(rule.id(), 0);
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

            final List<String> matchedRuleIds = new ArrayList<>();
            String winningRuleId = null;
            String stockProfileName = this.defaultStockProfileName(policy);
            String ecoEnvelopeName = this.defaultEcoEnvelopeName(policy);
            boolean stockProfileExplicit = false;
            boolean ecoEnvelopeExplicit = false;
            String postRuleAdjustment = null;
            String note = null;

            for (final AdminCatalogPolicyRule rule : rules) {
                if (!rule.matches(facts, finalCategory, derivation)) {
                    continue;
                }
                ruleMatchCounts.computeIfPresent(rule.id(), (ignored, value) -> Integer.valueOf(value.intValue() + 1));
                matchedRuleIds.add(rule.id());
                winningRuleId = rule.id();
                if (rule.policy() != null) {
                    policy = rule.policy();
                    if (!stockProfileExplicit) {
                        stockProfileName = this.defaultStockProfileName(policy);
                    }
                    if (!ecoEnvelopeExplicit) {
                        ecoEnvelopeName = this.defaultEcoEnvelopeName(policy);
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
            }

            final AdminCatalogManualOverride override = overrides.get(facts.key());
            if (override != null) {
                if (override.policy() != null) {
                    policy = override.policy();
                    if (!this.hasText(override.stockProfile())) {
                        stockProfileName = this.defaultStockProfileName(policy);
                        stockProfileExplicit = false;
                    }
                    if (!this.hasText(override.ecoEnvelope())) {
                        ecoEnvelopeName = this.defaultEcoEnvelopeName(policy);
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
                stockProfileName = this.defaultStockProfileName(policy);
                ecoEnvelopeName = this.defaultEcoEnvelopeName(policy);
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
            proposedEntries,
            liveEntries,
            decisionTraces,
            diffEntries,
            validationIssues,
            ruleMatchCounts
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
        final List<AdminCatalogPlanEntry> proposedEntries,
        final List<AdminCatalogPlanEntry> liveEntries,
        final List<AdminCatalogDecisionTrace> decisionTraces,
        final List<AdminCatalogDiffEntry> diffEntries,
        final List<AdminCatalogValidationIssue> validationIssues,
        final Map<String, Integer> ruleMatchCounts
    ) throws IOException {
        this.writeGeneratedCatalog(new File(generatedDirectory, "generated-catalog.yml"), proposedEntries);
        this.writeGeneratedSummary(
            new File(generatedDirectory, "generated-summary.yml"),
            proposedEntries,
            liveEntries,
            validationIssues,
            ruleMatchCounts
        );
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
        final List<AdminCatalogPlanEntry> liveEntries,
        final List<AdminCatalogValidationIssue> validationIssues,
        final Map<String, Integer> ruleMatchCounts
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


```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/classify/DefaultCategoryClassifier.java`

```java
package com.splatage.wild_economy.catalog.classify;

import com.splatage.wild_economy.catalog.model.CatalogCategory;
import com.splatage.wild_economy.catalog.model.ItemFacts;
import java.util.Set;

public final class DefaultCategoryClassifier implements CategoryClassifier {

    private static final Set<String> FOOD_EXACT = Set.of(
        "apple",
        "bread",
        "cookie",
        "cake",
        "pumpkin_pie",
        "beetroot_soup",
        "mushroom_stew",
        "rabbit_stew",
        "suspicious_stew",
        "golden_apple",
        "enchanted_golden_apple",
        "dried_kelp",
        "honey_bottle"
    );

    private static final Set<String> FARMING_EXACT = Set.of(
        "wheat",
        "carrot",
        "potato",
        "beetroot",
        "melon_slice",
        "pumpkin",
        "melon",
        "sugar_cane",
        "bamboo",
        "cactus",
        "kelp",
        "sweet_berries",
        "glow_berries",
        "nether_wart"
    );

    private static final Set<String> MOB_DROP_EXACT = Set.of(
        "rotten_flesh",
        "bone",
        "arrow",
        "string",
        "spider_eye",
        "fermented_spider_eye",
        "gunpowder",
        "slime_ball",
        "magma_cream",
        "ender_pearl",
        "ghast_tear",
        "phantom_membrane",
        "feather",
        "leather",
        "ink_sac",
        "glow_ink_sac",
        "rabbit_hide",
        "prismarine_shard",
        "prismarine_crystals",
        "shulker_shell"
    );

    private static final Set<String> WOOD_FAMILY_PREFIXES = Set.of(
        "oak",
        "spruce",
        "birch",
        "jungle",
        "acacia",
        "dark_oak",
        "mangrove",
        "cherry",
        "pale_oak",
        "bamboo",
        "crimson",
        "warped"
    );

    @Override
    public CatalogCategory classify(final ItemFacts facts) {
        final String key = facts.key();

        if (isWood(key)) {
            return CatalogCategory.WOODS;
        }
        if (isStone(key)) {
            return CatalogCategory.STONE;
        }
        if (isOreOrMineral(key)) {
            return CatalogCategory.ORES_AND_MINERALS;
        }
        if (isFarming(key)) {
            return CatalogCategory.FARMING;
        }
        if (isFood(key, facts.edible())) {
            return CatalogCategory.FOOD;
        }
        if (isMobDrop(key)) {
            return CatalogCategory.MOB_DROPS;
        }
        if (isNether(key)) {
            return CatalogCategory.NETHER;
        }
        if (isEnd(key)) {
            return CatalogCategory.END;
        }
        if (isRedstone(key)) {
            return CatalogCategory.REDSTONE;
        }
        if (isBrewing(key)) {
            return CatalogCategory.BREWING;
        }
        if (isTool(key)) {
            return CatalogCategory.TOOLS;
        }
        if (isCombat(key)) {
            return CatalogCategory.COMBAT;
        }
        if (isTransport(key)) {
            return CatalogCategory.TRANSPORT;
        }
        if (isDecoration(key)) {
            return CatalogCategory.DECORATION;
        }

        return CatalogCategory.MISC;
    }

    private boolean isWood(final String key) {
        return key.endsWith("_log")
            || key.endsWith("_wood")
            || key.endsWith("_planks")
            || key.endsWith("_sapling")
            || key.endsWith("_leaves")
            || key.endsWith("_hanging_sign")
            || key.endsWith("_sign")
            || key.endsWith("_boat")
            || key.endsWith("_chest_boat")
            || key.endsWith("_raft")
            || key.endsWith("_chest_raft")
            || key.contains("mangrove")
            || key.contains("bamboo_block")
            || key.contains("bamboo_planks")
            || key.contains("bamboo_mosaic")
            || key.contains("stripped_")
            || isWoodFamilyCrafted(key);
    }

    private boolean isWoodFamilyCrafted(final String key) {
        for (final String prefix : WOOD_FAMILY_PREFIXES) {
            if (!key.startsWith(prefix + "_")) {
                continue;
            }
            return key.endsWith("_button")
                || key.endsWith("_door")
                || key.endsWith("_fence")
                || key.endsWith("_fence_gate")
                || key.endsWith("_trapdoor")
                || key.endsWith("_pressure_plate")
                || key.endsWith("_slab")
                || key.endsWith("_stairs");
        }
        return false;
    }

    private boolean isStone(final String key) {
        return key.contains("stone")
            || key.contains("cobble")
            || key.contains("deepslate")
            || key.contains("tuff")
            || key.contains("andesite")
            || key.contains("granite")
            || key.contains("diorite")
            || key.contains("blackstone")
            || key.contains("basalt")
            || key.contains("calcite")
            || key.contains("dripstone")
            || key.contains("brick")
            || key.equals("smooth_quartz")
            || key.equals("quartz_bricks");
    }

    private boolean isOreOrMineral(final String key) {
        return key.contains("_ore")
            || key.startsWith("raw_")
            || key.endsWith("_ingot")
            || key.endsWith("_nugget")
            || key.equals("coal")
            || key.equals("charcoal")
            || key.equals("diamond")
            || key.equals("emerald")
            || key.equals("lapis_lazuli")
            || key.equals("redstone")
            || key.equals("amethyst_shard")
            || key.equals("quartz")
            || key.equals("netherite_scrap")
            || key.equals("netherite_ingot")
            || key.endsWith("_block") && (
                key.startsWith("iron_")
                    || key.startsWith("gold_")
                    || key.startsWith("diamond_")
                    || key.startsWith("emerald_")
                    || key.startsWith("lapis_")
                    || key.startsWith("redstone_")
                    || key.startsWith("copper_")
                    || key.startsWith("coal_")
                    || key.startsWith("amethyst_")
                    || key.startsWith("netherite_")
            );
    }

    private boolean isFarming(final String key) {
        return FARMING_EXACT.contains(key)
            || key.endsWith("_seeds")
            || key.equals("wheat_seeds")
            || key.equals("beetroot_seeds")
            || key.equals("melon_seeds")
            || key.equals("pumpkin_seeds")
            || key.equals("torchflower_seeds")
            || key.equals("pitcher_pod")
            || key.contains("crop")
            || key.contains("farmland")
            || key.contains("cocoa")
            || key.contains("vine")
            || key.contains("propagule");
    }

    private boolean isFood(final String key, final boolean edible) {
        return edible
            || FOOD_EXACT.contains(key)
            || key.startsWith("cooked_")
            || key.startsWith("raw_")
            || key.endsWith("_meat")
            || key.endsWith("_stew")
            || key.endsWith("_slice")
            || key.endsWith("_berries")
            || key.equals("egg");
    }

    private boolean isMobDrop(final String key) {
        return MOB_DROP_EXACT.contains(key)
            || key.endsWith("_spawn_egg");
    }

    private boolean isNether(final String key) {
        return key.contains("nether")
            || key.contains("blaze")
            || key.contains("ghast")
            || key.contains("magma")
            || key.contains("netherrack")
            || key.contains("crimson")
            || key.contains("warped")
            || key.contains("soul_")
            || key.contains("blackstone")
            || key.contains("ancient_debris")
            || key.contains("shroomlight")
            || key.contains("glowstone");
    }

    private boolean isEnd(final String key) {
        return key.contains("end_")
            || key.contains("chorus")
            || key.contains("purpur")
            || key.contains("shulker")
            || key.contains("elytra")
            || key.contains("dragon_");
    }

    private boolean isRedstone(final String key) {
        return key.equals("redstone")
            || key.contains("repeater")
            || key.contains("comparator")
            || key.contains("observer")
            || key.contains("piston")
            || key.contains("hopper")
            || key.contains("dropper")
            || key.contains("dispenser")
            || key.contains("daylight_detector")
            || key.contains("target")
            || key.contains("lectern")
            || key.contains("tripwire")
            || key.contains("pressure_plate")
            || key.contains("redstone_lamp")
            || key.contains("sculk_sensor")
            || key.contains("calibrated_sculk_sensor");
    }

    private boolean isBrewing(final String key) {
        return key.contains("potion")
            || key.contains("brewing")
            || key.contains("cauldron")
            || key.contains("blaze_powder")
            || key.contains("ghast_tear")
            || key.contains("glistering_melon")
            || key.contains("nether_wart")
            || key.contains("dragon_breath")
            || key.contains("phantom_membrane");
    }

    private boolean isTool(final String key) {
        return key.endsWith("_pickaxe")
            || key.endsWith("_axe")
            || key.endsWith("_shovel")
            || key.endsWith("_hoe")
            || key.endsWith("_shears")
            || key.endsWith("_bucket")
            || key.equals("flint_and_steel")
            || key.equals("fishing_rod")
            || key.equals("carrot_on_a_stick")
            || key.equals("warped_fungus_on_a_stick")
            || key.equals("brush")
            || key.equals("spyglass")
            || key.equals("clock")
            || key.equals("compass")
            || key.equals("recovery_compass");
    }

    private boolean isCombat(final String key) {
        return key.endsWith("_sword")
            || key.endsWith("_helmet")
            || key.endsWith("_chestplate")
            || key.endsWith("_leggings")
            || key.endsWith("_boots")
            || key.equals("bow")
            || key.equals("crossbow")
            || key.equals("trident")
            || key.equals("shield")
            || key.equals("arrow")
            || key.equals("spectral_arrow")
            || key.equals("tipped_arrow")
            || key.contains("mace");
    }

    private boolean isTransport(final String key) {
        return key.contains("minecart")
            || key.contains("rail")
            || key.contains("boat")
            || key.contains("raft")
            || key.equals("saddle")
            || key.equals("lead");
    }

    private boolean isDecoration(final String key) {
        return key.contains("banner")
            || key.contains("bed")
            || key.contains("carpet")
            || key.contains("painting")
            || key.contains("flower_pot")
            || key.contains("candle")
            || key.contains("lantern")
            || key.contains("glass")
            || key.contains("terracotta")
            || key.contains("concrete")
            || key.contains("coral")
            || key.contains("pottery_sherd")
            || key.contains("head")
            || key.contains("skull")
            || key.contains("frame")
            || key.contains("amethyst_cluster")
            || key.contains("sea_pickle");
    }
}

```

---

## File: `src/main/java/com/splatage/wild_economy/catalog/recipe/WoodFamilyRecipeFallbacks.java`

```java
package com.splatage.wild_economy.catalog.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

public final class WoodFamilyRecipeFallbacks {

    private static final List<WoodFamily> WOOD_FAMILIES = List.of(
        WoodFamily.normal("oak"),
        WoodFamily.normal("spruce"),
        WoodFamily.normal("birch"),
        WoodFamily.normal("jungle"),
        WoodFamily.normal("acacia"),
        WoodFamily.normal("dark_oak"),
        WoodFamily.normal("mangrove"),
        WoodFamily.normal("cherry"),
        WoodFamily.normal("pale_oak"),
        WoodFamily.nether("crimson"),
        WoodFamily.nether("warped")
    );

    private WoodFamilyRecipeFallbacks() {
    }

    public static void apply(final Map<String, List<RecipeDefinition>> recipesByOutput) {
        for (final WoodFamily family : WOOD_FAMILIES) {
            applyPlanksFromLog(recipesByOutput, family);
            applyWoodFromLogs(recipesByOutput, family);
            applyWoodenSign(recipesByOutput, family);
            applyHangingSign(recipesByOutput, family);
            applyBoat(recipesByOutput, family);
            applyChestBoat(recipesByOutput, family);
        }
    }

    private static void applyPlanksFromLog(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!hasMaterial(family.inputLogKey()) || !hasMaterial(family.planksKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.planksKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.planksKey(),
                4,
                "fallback_planks_from_log",
                List.of(new RecipeIngredient(family.inputLogKey(), 1))
            )
        );
    }

    private static void applyWoodFromLogs(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!hasMaterial(family.inputLogKey()) || !hasMaterial(family.woodLikeOutputKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.woodLikeOutputKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.woodLikeOutputKey(),
                3,
                "fallback_wood_from_logs",
                List.of(new RecipeIngredient(family.inputLogKey(), 4))
            )
        );
    }

    private static void applyWoodenSign(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.signKey()) || !hasMaterial("stick")) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.signKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.signKey(),
                3,
                "fallback_wooden_sign",
                List.of(
                    new RecipeIngredient(family.planksKey(), 6),
                    new RecipeIngredient("stick", 1)
                )
            )
        );
    }

    private static void applyHangingSign(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!hasMaterial(family.strippedInputLogKey()) || !hasMaterial(family.hangingSignKey()) || !hasMaterial("chain")) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.hangingSignKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.hangingSignKey(),
                6,
                "fallback_hanging_sign",
                List.of(
                    new RecipeIngredient(family.strippedInputLogKey(), 6),
                    new RecipeIngredient("chain", 2)
                )
            )
        );
    }

    private static void applyBoat(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!family.supportsBoatRecipes()) {
            return;
        }
        if (!hasMaterial(family.planksKey()) || !hasMaterial(family.boatKey())) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.boatKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.boatKey(),
                1,
                "fallback_boat",
                List.of(new RecipeIngredient(family.planksKey(), 5))
            )
        );
    }

    private static void applyChestBoat(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final WoodFamily family
    ) {
        if (!family.supportsBoatRecipes()) {
            return;
        }
        if (!hasMaterial(family.boatKey()) || !hasMaterial(family.chestBoatKey()) || !hasMaterial("chest")) {
            return;
        }
        if (hasRecipes(recipesByOutput, family.chestBoatKey())) {
            return;
        }

        addRecipe(
            recipesByOutput,
            new RecipeDefinition(
                family.chestBoatKey(),
                1,
                "fallback_chest_boat",
                List.of(
                    new RecipeIngredient(family.boatKey(), 1),
                    new RecipeIngredient("chest", 1)
                )
            )
        );
    }

    private static boolean hasRecipes(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final String outputKey
    ) {
        final List<RecipeDefinition> definitions = recipesByOutput.get(outputKey);
        return definitions != null && !definitions.isEmpty();
    }

    private static void addRecipe(
        final Map<String, List<RecipeDefinition>> recipesByOutput,
        final RecipeDefinition recipeDefinition
    ) {
        recipesByOutput.computeIfAbsent(recipeDefinition.outputKey(), ignored -> new ArrayList<>())
            .add(recipeDefinition);
    }

    private static boolean hasMaterial(final String itemKey) {
        final String enumName = itemKey.toUpperCase(Locale.ROOT);
        return Material.matchMaterial(enumName) != null;
    }

    private record WoodFamily(
        String familyKey,
        String inputLogKey,
        String strippedInputLogKey,
        String planksKey,
        String woodLikeOutputKey,
        String signKey,
        String hangingSignKey,
        String boatKey,
        String chestBoatKey,
        boolean supportsBoatRecipes
    ) {
        private static WoodFamily normal(final String familyKey) {
            return new WoodFamily(
                familyKey,
                familyKey + "_log",
                "stripped_" + familyKey + "_log",
                familyKey + "_planks",
                familyKey + "_wood",
                familyKey + "_sign",
                familyKey + "_hanging_sign",
                familyKey + "_boat",
                familyKey + "_chest_boat",
                true
            );
        }

        private static WoodFamily nether(final String familyKey) {
            return new WoodFamily(
                familyKey,
                familyKey + "_stem",
                "stripped_" + familyKey + "_stem",
                familyKey + "_planks",
                familyKey + "_hyphae",
                familyKey + "_sign",
                familyKey + "_hanging_sign",
                null,
                null,
                false
            );
        }
    }
}

```
