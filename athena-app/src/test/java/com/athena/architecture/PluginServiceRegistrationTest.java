package com.athena.architecture;

import com.athena.semantic.spi.FrameworkPlugin;
import com.athena.semantic.spi.LanguagePlugin;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one part of "insert a new plugin correctly" that ArchUnit
 * itself can't express: {@code META-INF/services} is a classpath
 * <em>resource</em>, not bytecode structure, so a class can implement
 * {@link LanguagePlugin}/{@link FrameworkPlugin} perfectly and still never
 * be discovered at runtime if whoever added it forgot the service file —
 * {@link java.util.ServiceLoader} fails silently in that case (no error,
 * the plugin is just absent from {@code PluginRegistry}'s lists).
 *
 * <p>Cross-checks in both directions: every implementation is registered,
 * and every registration resolves to a real implementation (catches a typo
 * in the service file, or a class renamed/deleted without updating it).
 */
class PluginServiceRegistrationTest {

    private static final JavaClasses PLUGIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.athena.plugin");

    @Test
    void everyLanguagePluginImplementationIsRegisteredInMetaInfServices() {
        assertServiceRegistrationIsComplete(LanguagePlugin.class);
    }

    @Test
    void everyFrameworkPluginImplementationIsRegisteredInMetaInfServices() {
        assertServiceRegistrationIsComplete(FrameworkPlugin.class);
    }

    private <T> void assertServiceRegistrationIsComplete(Class<T> pluginInterface) {
        Set<String> implementationClassNames = PLUGIN_CLASSES.stream()
                .filter(javaClass -> javaClass.isAssignableTo(pluginInterface))
                .filter(javaClass -> !javaClass.isInterface())
                .map(JavaClass::getFullName)
                .collect(Collectors.toSet());

        Set<String> registeredClassNames = ServiceLoader.load(pluginInterface).stream()
                .map(provider -> provider.type().getName())
                .collect(Collectors.toSet());

        assertThat(registeredClassNames)
                .as("every class implementing %s must be listed in META-INF/services/%s, "
                                + "or it silently won't be discovered by ServiceLoader at runtime",
                        pluginInterface.getSimpleName(), pluginInterface.getName())
                .containsExactlyInAnyOrderElementsOf(implementationClassNames);
    }
}
