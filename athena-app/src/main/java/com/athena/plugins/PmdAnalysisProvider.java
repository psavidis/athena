package com.athena.plugins;

import com.athena.analysis.spi.ExternalAnalysisProvider;
import com.athena.analysis.spi.ExternalFinding;
import com.athena.analysis.spi.ExternalFindingSeverity;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.analysis.spi.ProviderRunResult;
import com.athena.analysis.spi.SourceLocation;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * PMD (ticket #114/#116) as a concrete {@link ExternalAnalysisProvider}: runs PMD's
 * documented, public in-process embedding API ({@link PmdAnalysis}/{@link
 * PMDConfiguration} — not the SonarJava {@code CheckVerifier} testing-only path this
 * ticket originally considered and rejected, see the ticket's "Provider swap" note)
 * against the given source tree, mapping each {@link RuleViolation} PMD reports into
 * a provider-independent {@link ExternalFinding}. This is the one class in Athena
 * allowed to import {@code net.sourceforge.pmd..} (see {@code
 * ExternalProviderModuleBoundaryTest}) — PMD's own concepts (rulesets, {@link
 * RulePriority}, {@link RuleViolation}) never escape this package.
 *
 * <p>PMD's own analysis is designed to never throw for an ordinary misconfiguration
 * (a ruleset it can't load, a file it can't parse) — it accumulates such problems
 * into its own reporter instead of propagating an exception. {@link #analyze}
 * therefore inspects {@link PmdAnalysis#getRulesets()} once PMD has attempted to load
 * the configured ruleset: if none loaded, that is this provider's own signal to
 * report a failed {@link ProviderRunResult}, per {@link
 * ExternalAnalysisProvider#analyze}'s documented contract. A defensive top-level
 * catch additionally isolates any other, genuinely unexpected exception PMD or one
 * of its dependencies might still throw.
 */
public final class PmdAnalysisProvider implements ExternalAnalysisProvider {

    private static final String PROVIDER_ID = "pmd";
    private static final String LANGUAGE = "java";
    private static final String DEFAULT_RULESET = "rulesets/java/quickstart.xml";

    /**
     * Key into {@link ProviderConfiguration#settings()} selecting a ruleset other than
     * the default (ticket #116's "start with a reasonable default ruleset" scope note)
     * — opaque to Athena's core, meaningful only to this provider.
     */
    public static final String RULESET_SETTING = "ruleset";

    /**
     * PMD's five-level {@link RulePriority} (HIGH..LOW) maps one-to-one, in the same
     * descending-severity order, onto {@link ExternalFindingSeverity}'s five levels
     * (BLOCKER..INFO) — ticket #116's documented severity mapping. PMD's own HIGH
     * ("change absolutely required, behavior is critically broken") is Athena's most
     * severe BLOCKER; PMD's LOW ("nice to have") is Athena's INFO. A plain lookup map
     * rather than a switch on {@link RulePriority} (CODE_STYLE.md's enum guidance) —
     * a switch on a foreign enum also has javac lower it into a synthetic
     * {@code $SwitchMap} nested class, which would itself need exempting from
     * ExternalProviderModuleBoundaryTest's "only PmdAnalysisProvider touches PMD
     * types" rule.
     */
    private static final Map<RulePriority, ExternalFindingSeverity> SEVERITY_BY_PRIORITY = Map.of(
            RulePriority.HIGH, ExternalFindingSeverity.BLOCKER,
            RulePriority.MEDIUM_HIGH, ExternalFindingSeverity.HIGH,
            RulePriority.MEDIUM, ExternalFindingSeverity.MEDIUM,
            RulePriority.MEDIUM_LOW, ExternalFindingSeverity.LOW,
            RulePriority.LOW, ExternalFindingSeverity.INFO);

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String language() {
        return LANGUAGE;
    }

    @Override
    public ProviderRunResult analyze(Path sourceRoot, List<Path> changedFiles, ProviderConfiguration configuration) {
        if (!Files.isDirectory(sourceRoot)) {
            return ProviderRunResult.failure("source root does not exist or is not a directory: " + sourceRoot);
        }
        try {
            return runPmd(sourceRoot, rulesetOf(configuration));
        } catch (RuntimeException e) {
            return ProviderRunResult.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private ProviderRunResult runPmd(Path sourceRoot, String ruleset) {
        Language java = LanguageRegistry.PMD.getLanguageById(LANGUAGE);
        if (java == null) {
            return ProviderRunResult.failure("PMD has no registered support for language: " + LANGUAGE);
        }

        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(java.getDefaultVersion());
        config.addInputPath(sourceRoot);
        config.addRuleSet(ruleset);

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            if (pmd.getRulesets().isEmpty()) {
                return ProviderRunResult.failure("PMD could not load any rules from ruleset: " + ruleset);
            }
            Report report = pmd.performAnalysisAndCollectReport();
            List<ExternalFinding> findings = report.getViolations().stream()
                    .map(violation -> toExternalFinding(violation, sourceRoot))
                    .toList();
            return ProviderRunResult.success(findings);
        }
    }

    private String rulesetOf(ProviderConfiguration configuration) {
        return configuration.settings().getOrDefault(RULESET_SETTING, DEFAULT_RULESET);
    }

    private ExternalFinding toExternalFinding(RuleViolation violation, Path sourceRoot) {
        Rule rule = violation.getRule();
        SourceLocation location = SourceLocation.ofRange(relativePathOf(violation, sourceRoot),
                violation.getBeginLine(), violation.getEndLine());
        return ExternalFinding.builder(PROVIDER_ID, LANGUAGE, rule.getName(), severityOf(rule.getPriority()),
                        violation.getDescription(), location)
                .ruleName(rule.getName())
                .providerSpecificMetadata(Map.of("ruleSet", rule.getRuleSetName()))
                .build();
    }

    private String relativePathOf(RuleViolation violation, Path sourceRoot) {
        Path absolute = Path.of(violation.getFileId().getAbsolutePath());
        return sourceRoot.relativize(absolute).toString();
    }

    /** Package-visible for a dedicated, exhaustive unit test — see PmdAnalysisProviderTest. */
    static ExternalFindingSeverity severityOf(RulePriority priority) {
        ExternalFindingSeverity severity = SEVERITY_BY_PRIORITY.get(priority);
        if (severity == null) {
            throw new IllegalStateException("no documented severity mapping for PMD RulePriority: " + priority);
        }
        return severity;
    }
}
