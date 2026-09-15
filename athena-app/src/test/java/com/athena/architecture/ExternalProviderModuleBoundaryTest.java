package com.athena.architecture;

import com.athena.plugins.PmdAnalysisProvider;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ticket #116's "PMD-specific concepts do not leak into Athena's core
 * analysis model" requirement mechanically rather than by review: only {@link
 * PmdAnalysisProvider} may reference {@code net.sourceforge.pmd..} — every other
 * class (athena-core doesn't even have {@code pmd-java} on its classpath; every
 * other athena-app class, e.g. web controllers) must consume PMD's findings only
 * through the provider-independent {@code ExternalAnalysisProvider}/{@code
 * ExternalFinding} contract (see #148/#149).
 */
class ExternalProviderModuleBoundaryTest {

    private static final JavaClasses APP_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.athena");

    @Test
    void onlyThePmdProviderMayReferencePmdsOwnTypes() {
        ArchRule rule = noClasses().that().areNotAssignableTo(PmdAnalysisProvider.class)
                .should().dependOnClassesThat().resideInAPackage("net.sourceforge.pmd..")
                .because("PMD's own concepts (rulesets, RulePriority, RuleViolation) must stay inside "
                        + "PmdAnalysisProvider — every other class consumes only the provider-independent "
                        + "ExternalAnalysisProvider/ExternalFinding contract (ticket #114/#116)");

        rule.check(APP_CLASSES);
    }
}
