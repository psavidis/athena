package com.athena.plugins;

import com.athena.semantic.AnalysisResult;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.SemanticProfile;
import com.athena.semantic.TransformationKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class PrAnalyzerSemanticProfileTest {

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-semantic-profile-base");
        headRoot = Files.createTempDirectory("athena-semantic-profile-head");
    }

    @AfterEach
    void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Test
    void everyDetectedChangeGetsExactlyOneSemanticProfileInTheSameOrder() {
        write(baseRoot, "Guard", "public class Guard {\n"
                + "    public boolean isAllowed() { return true; }\n"
                + "}\n");
        write(headRoot, "Guard", "public class Guard {\n"
                + "    public boolean isPermitted() { return true; }\n"
                + "}\n");

        AnalysisResult result = newPrAnalyzer().analyze(baseRoot, headRoot);

        assertThat(result.semanticProfiles()).hasSameSizeAs(result.changes());
        assertThat(result.semanticProfiles()).extracting(SemanticProfile::change)
                .containsExactlyElementsOf(result.changes());
    }

    @Test
    void aRenameChangeIsClassifiedAsRenameInTheStructuralDimension() {
        write(baseRoot, "Guard", "public class Guard {\n"
                + "    public boolean isAllowed() { return true; }\n"
                + "}\n");
        write(headRoot, "Guard", "public class Guard {\n"
                + "    public boolean isPermitted() { return true; }\n"
                + "}\n");

        AnalysisResult result = newPrAnalyzer().analyze(baseRoot, headRoot);

        Change renameChange = result.changes().stream()
                .filter(c -> c.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst().orElseThrow();
        SemanticProfile profile = result.semanticProfileFor(renameChange);

        assertThat(profile.classifiedDimensions()).contains(SemanticDimension.STRUCTURAL);
        assertThat(profile.classifications(SemanticDimension.STRUCTURAL))
                .extracting(c -> c.concept().id())
                .containsExactly("rename");
    }

    @Test
    void aNewlyAddedControllerClassIsClassifiedAlongMultipleCoexistingDimensions() {
        write(headRoot, "UserController", "public class UserController {\n}\n");

        AnalysisResult result = newPrAnalyzer().analyze(baseRoot, headRoot);

        Change addChange = result.changes().stream()
                .filter(c -> c.kind() == TransformationKind.ADD_CLASS)
                .findFirst().orElseThrow();
        SemanticProfile profile = result.semanticProfileFor(addChange);

        assertThat(profile.classifications(SemanticDimension.RESPONSIBILITY))
                .extracting(c -> c.concept().id()).containsExactly("add-capability");
        assertThat(profile.classifications(SemanticDimension.ARCHITECTURE))
                .extracting(c -> c.concept().id()).containsExactly("driving-adapter");
    }

    @Test
    void endToEndFieldRemovedAndReplacedByAConstructorParameterIsClassifiedAsDependencyInjection() {
        // One of the two shapes PatternTaxonomyClassifier.classifyDependencyInjection
        // recognizes: the field declaration is deleted outright (a different type here, since
        // a same-name-same-type field is matched as "the same field" and never reported as
        // REMOVE_FIELD) and the constructor picks up a same-named parameter.
        write(baseRoot, "UserService", "public class UserService {\n"
                + "    private OldRepository userRepository;\n"
                + "    public UserService() {\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "UserService", "public class UserService {\n"
                + "    private final UserRepository userRepository;\n"
                + "    public UserService(UserRepository userRepository) {\n"
                + "        this.userRepository = userRepository;\n"
                + "    }\n"
                + "}\n");

        AnalysisResult result = newPrAnalyzer().analyze(baseRoot, headRoot);

        Change addedParameterChange = result.changes().stream()
                .filter(c -> c.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER)
                .findFirst().orElseThrow();
        SemanticProfile profile = result.semanticProfileFor(addedParameterChange);

        assertThat(profile.classifications(SemanticDimension.PATTERN))
                .extracting(c -> c.concept().id()).containsExactly("dependency-injection");
    }

    @Test
    void endToEndAutowiredFieldDroppedInFavorOfAConstructorParameterIsClassifiedAsDependencyInjection() {
        // The ticket's literal example and the common real-world shape: the field stays
        // declared (same name, same type) but loses @Autowired, and a constructor appears
        // that assigns a same-named parameter to it.
        write(baseRoot, "UserService", "public class UserService {\n"
                + "    @Autowired\n"
                + "    private UserRepository userRepository;\n"
                + "}\n");
        write(headRoot, "UserService", "public class UserService {\n"
                + "    private final UserRepository userRepository;\n"
                + "    public UserService(UserRepository userRepository) {\n"
                + "        this.userRepository = userRepository;\n"
                + "    }\n"
                + "}\n");

        AnalysisResult result = newPrAnalyzer().analyze(baseRoot, headRoot);

        Change addedParameterChange = result.changes().stream()
                .filter(c -> c.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER)
                .findFirst().orElseThrow();
        SemanticProfile profile = result.semanticProfileFor(addedParameterChange);

        assertThat(profile.classifications(SemanticDimension.PATTERN))
                .extracting(c -> c.concept().id()).containsExactly("dependency-injection");
    }

    @Test
    void semanticProfileForAnUnknownChangeReturnsAnEmptyProfileRatherThanThrowing() {
        AnalysisResult result = newPrAnalyzer().analyze(baseRoot, headRoot);
        Change foreign = new ChangeGrouper().group(java.util.List.of(
                DetectedTransformation.of(TransformationKind.ADD_SYMBOL, java.util.List.of("Other#m"), java.util.List.of())
        )).get(0);

        SemanticProfile profile = result.semanticProfileFor(foreign);

        assertThat(profile.classifiedDimensions()).isEmpty();
        assertThat(profile.change()).isSameAs(foreign);
    }

    private PrAnalyzer newPrAnalyzer() {
        return new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());
    }

    private void write(Path root, String simpleName, String content) {
        try {
            Files.writeString(root.resolve(simpleName + ".java"), content);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp directory
                }
            });
        }
    }
}
