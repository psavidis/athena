package com.athena.plugin.spring;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.Taxonomy;
import com.athena.semantic.spi.FrameworkPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Spring {@link FrameworkPlugin} implementation: recognizes Spring/JPA/
 * Jackson/JUnit annotation conventions via {@link FrameworkTaxonomyClassifier}.
 * Discovered by the application via {@link java.util.ServiceLoader} (see
 * {@code META-INF/services}) — applies only to Changes detected by a
 * {@code "java"} {@link com.athena.semantic.spi.LanguagePlugin}.
 */
public final class SpringFrameworkPlugin implements FrameworkPlugin {

    private final FrameworkTaxonomyClassifier classifier = new FrameworkTaxonomyClassifier();

    @Override
    public String frameworkId() {
        return "spring-boot";
    }

    @Override
    public Set<String> supportedLanguageIds() {
        return Set.of("java");
    }

    @Override
    public Optional<SemanticClassification> classify(Change change, Taxonomy frameworkTaxonomy) {
        return classifier.classify(change, frameworkTaxonomy);
    }

    @Override
    public Map<Change, SemanticClassification> classifyCorrelated(List<Change> changes, Taxonomy frameworkTaxonomy) {
        Map<Change, SemanticClassification> correlated =
                new LinkedHashMap<>(classifier.classifySpringFieldToConstructorInjection(changes, frameworkTaxonomy));
        classifier.classifySpringAwareToConstructorInjection(changes, frameworkTaxonomy).forEach(correlated::putIfAbsent);
        return correlated;
    }
}
