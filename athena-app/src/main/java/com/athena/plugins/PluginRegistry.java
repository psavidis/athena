package com.athena.plugins;

import com.athena.semantic.spi.FrameworkPlugin;
import com.athena.semantic.spi.LanguagePlugin;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers every {@link LanguagePlugin}/{@link FrameworkPlugin} on the
 * classpath via {@link java.util.ServiceLoader} — the one place in the
 * application that knows plugin jars exist. Adding a future plugin module
 * (a new language, a new framework) only requires bundling its jar on the
 * classpath (see {@code athena-app}'s {@code pom.xml}); nothing here or in
 * any caller needs to change.
 *
 * <p>Used both by the Spring-managed web app ({@link PluginConfiguration})
 * and by the CLI, which has no Spring context.
 */
public final class PluginRegistry {

    private PluginRegistry() {
    }

    public static List<LanguagePlugin> languagePlugins() {
        return ServiceLoader.load(LanguagePlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    public static List<FrameworkPlugin> frameworkPlugins() {
        return ServiceLoader.load(FrameworkPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }
}
