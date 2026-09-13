package com.athena.architecture;

import com.athena.semantic.spi.FrameworkPlugin;
import com.athena.semantic.spi.LanguagePlugin;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the plugin architecture's module conventions (see CLAUDE.md's
 * "adding a new language/framework should require no other code changes")
 * mechanically rather than by review — this is the one module whose
 * classpath actually has athena-core's SPI and every plugin module's
 * implementation classes together (athena-plugin-java/athena-plugin-spring
 * are {@code runtime}-scope dependencies here, which Maven still puts on
 * the test classpath), so it's the only place these cross-module rules can
 * run.
 *
 * <p>For the corresponding "META-INF/services registration is complete and
 * correct" check — which ArchUnit can't express, since it needs to read a
 * classpath resource rather than bytecode structure — see {@link
 * PluginServiceRegistrationTest}.
 */
class PluginModuleBoundaryTest {

    /**
     * com.athena.plugins is the one package allowed to name a concrete plugin
     * implementation class (see PluginRegistry/PluginConfiguration) — it's the
     * ServiceLoader discovery seam itself. Every other package must go through
     * the athena-core SPI only.
     */
    private static final String PLUGIN_WIRING_PACKAGE = "com.athena.plugins";

    private static final JavaClasses APP_AND_PLUGIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.athena");

    @Test
    void onlyThePluginWiringPackageMayReferenceAConcretePluginImplementation() {
        // Excludes com.athena.plugin.. itself: a plugin module's own classes referencing their
        // own sibling classes (e.g. JavaLanguagePlugin constructing a TransformationDetector) is
        // the plugin's own internals, not application code reaching into a plugin — only code
        // OUTSIDE every com.athena.plugin.. package is what this rule is actually guarding.
        ArchRule rule = noClasses().that().resideOutsideOfPackages(PLUGIN_WIRING_PACKAGE + "..", "com.athena.plugin..")
                .should().dependOnClassesThat().resideInAPackage("com.athena.plugin..")
                .because("athena-app must consume plugins only through the athena-core SPI "
                        + "(LanguagePlugin/FrameworkPlugin), discovered via PluginRegistry — never by "
                        + "importing a plugin module's own implementation class directly, or a future "
                        + "plugin swap would ripple through arbitrary application code instead of just "
                        + "PluginRegistry's ServiceLoader.load(...) call sites");

        rule.check(APP_AND_PLUGIN_CLASSES);
    }

    @Test
    void everyLanguagePluginImplementationLivesUnderThePluginPackageConvention() {
        ArchRule rule = classes().that().implement(LanguagePlugin.class)
                .should().resideInAPackage("com.athena.plugin..")
                .because("the plugin module convention is one top-level com.athena.plugin.<name> "
                        + "package per language/framework module — this is what "
                        + "onlyThePluginWiringPackageMayReferenceAConcretePluginImplementation() "
                        + "actually guards against, so a LanguagePlugin implementation living "
                        + "somewhere else would silently escape that rule too");

        rule.check(APP_AND_PLUGIN_CLASSES);
    }

    @Test
    void everyFrameworkPluginImplementationLivesUnderThePluginPackageConvention() {
        ArchRule rule = classes().that().implement(FrameworkPlugin.class)
                .should().resideInAPackage("com.athena.plugin..")
                .because("same convention as LanguagePlugin — see "
                        + "everyLanguagePluginImplementationLivesUnderThePluginPackageConvention()");

        rule.check(APP_AND_PLUGIN_CLASSES);
    }

    @Test
    void aLanguagePluginImplementationIsNeverAlsoAFrameworkPluginAndViceVersa() {
        ArchRule rule = noClasses().that().implement(LanguagePlugin.class)
                .should().implement(FrameworkPlugin.class)
                .because("the two plugin axes are independent by design (a language plugin parses/detects; "
                        + "a framework plugin only classifies the FRAMEWORK dimension on top of an existing "
                        + "language's Changes) — one class doing both would blur a boundary PrAnalyzer's own "
                        + "orchestration (separate languagePlugins/frameworkPlugins lists) assumes stays clean");

        rule.check(APP_AND_PLUGIN_CLASSES);
    }
}
