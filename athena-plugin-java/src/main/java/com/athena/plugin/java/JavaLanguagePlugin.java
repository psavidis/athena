package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.spi.LanguagePlugin;
import com.athena.semantic.spi.ParseOutcome;

import java.nio.file.Path;
import java.util.List;

/**
 * The Java {@link LanguagePlugin} implementation: adapts {@link JavaSourceParser}
 * and {@link TransformationDetector} to the core SPI, so {@code athena-core}
 * never needs to know JavaParser exists. Discovered by the application via
 * {@link java.util.ServiceLoader} (see {@code META-INF/services}).
 */
public final class JavaLanguagePlugin implements LanguagePlugin {

    private final JavaSourceParser parser = new JavaSourceParser();
    private final TransformationDetector detector = new TransformationDetector();

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public boolean supports(Path sourceFile) {
        return sourceFile.toString().endsWith(".java");
    }

    @Override
    public ParseOutcome checkParses(String sourceText) {
        ParseResult result = parser.parse(sourceText);
        return result.isSuccessful() ? ParseOutcome.success() : ParseOutcome.failure(result.errorMessage());
    }

    @Override
    public List<DetectedTransformation> detect(Path baseRoot, Path headRoot) {
        return detector.detect(baseRoot, headRoot);
    }
}
