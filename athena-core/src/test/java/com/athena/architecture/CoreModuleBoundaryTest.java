package com.athena.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces athena-core's own half of the plugin architecture's IoC boundary
 * at the source level, in addition to the dependency:tree proof (`mvn
 * dependency:tree -pl athena-core` never showing javaparser/spring). A
 * dependency reaching this module's classpath by accident (e.g. someone adds
 * it to this module's pom.xml as a transitive convenience) would still let
 * these imports compile — this test catches that even if dependency:tree
 * isn't checked in CI.
 *
 * <p>Every language/framework this module must stay ignorant of is a new
 * assertion here — see athena-app's {@code PluginModuleBoundaryTest} for the
 * rules that need every module's classes on the classpath at once (this
 * module, by design, never has more than its own).
 */
class CoreModuleBoundaryTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);

    @Test
    void noCoreClassDependsOnJavaParser() {
        ArchRule rule = noClasses().that().resideInAPackage("com.athena..")
                .should().dependOnClassesThat().resideInAPackage("com.github.javaparser..")
                .because("athena-core must stay language-agnostic — JavaParser is athena-plugin-java's concern");

        rule.check(IMPORTER.importPackages("com.athena"));
    }

    @Test
    void noCoreClassDependsOnSpring() {
        ArchRule rule = noClasses().that().resideInAPackage("com.athena..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("athena-core must stay usable outside a Spring context — Spring is athena-app's concern");

        rule.check(IMPORTER.importPackages("com.athena"));
    }
}
