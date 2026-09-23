package com.athena.plugin.java;

import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code pull_up_detection.feature} (ticket #290): members several classes lose
 * to a newly added base class are one pull-up, and detection is deterministic.
 */
public class PullUpSteps {

    private static final Set<TransformationKind> REMOVE_MOVE_ADD = EnumSet.of(
            TransformationKind.REMOVE_FIELD, TransformationKind.MOVE_FIELD, TransformationKind.ADD_FIELD,
            TransformationKind.REMOVE_SYMBOL, TransformationKind.MOVE_SYMBOL, TransformationKind.ADD_SYMBOL);

    private final DetectionWorld world;
    private List<String> classes = List.of();
    private String member;
    private String memberType;
    private boolean method;
    private List<DetectedTransformation> secondRun;

    public PullUpSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where classes {string}, {string} and {string} each declare field {string} of type {string}")
    public void base_three_classes_declare_field(String a, String b, String c, String field, String type) {
        baseDeclares(List.of(a, b, c), field, type, false);
    }

    @Given("a base revision where classes {string} and {string} each declare field {string} of type {string}")
    public void base_two_classes_declare_field(String a, String b, String field, String type) {
        baseDeclares(List.of(a, b), field, type, false);
    }

    @Given("a base revision where classes {string} and {string} each declare method {string} returning {string}")
    public void base_two_classes_declare_method(String a, String b, String name, String type) {
        baseDeclares(List.of(a, b), name, type, true);
    }

    @Given("a head revision where new class {string} declares field {string} of type {string} and those classes extend it without the field")
    public void head_pulls_field_up(String base, String field, String type) {
        headPullsUp(base, classes, List.of());
    }

    @Given("a head revision where new class {string} declares method {string} returning {string} and those classes extend it without the method")
    public void head_pulls_method_up(String base, String name, String type) {
        headPullsUp(base, classes, List.of());
    }

    @Given("a head revision where new class {string} declares field {string} of type {string} and those classes extend new class {string} which extends it, without the field")
    public void head_pulls_field_up_two_levels(String base, String field, String type, String intermediate) {
        write(world.headRoot(), base, "public abstract class " + base + " {\n" + memberSource() + "}\n");
        write(world.headRoot(), intermediate, "public abstract class " + intermediate + " extends " + base + " {\n}\n");
        for (String subclass : classes) {
            write(world.headRoot(), subclass, "public class " + subclass + " extends " + intermediate + " {\n}\n");
        }
    }

    @Given("a head revision where new class {string} declares field {string} of type {string} and only {string} extends it without the field")
    public void head_moves_field_from_one_class(String base, String field, String type, String onlySubclass) {
        headPullsUp(base, List.of(onlySubclass), classes.stream().filter(c -> !c.equals(onlySubclass)).toList());
    }

    @Given("a head revision where new class {string} declares field {string} of type {string} and those classes extend it, but {string} keeps its own field")
    public void head_pulls_field_up_but_one_keeps_it(String base, String field, String type, String keeper) {
        write(world.headRoot(), base, "public abstract class " + base + " {\n" + memberSource() + "}\n");
        for (String subclass : classes) {
            String body = subclass.equals(keeper) ? memberSource() : "";
            write(world.headRoot(), subclass, "public class " + subclass + " extends " + base + " {\n" + body + "}\n");
        }
    }

    @When("the semantic engine detects transformations between the revisions twice")
    public void detect_twice() {
        world.recordTransformations(new TransformationDetector().detect(world.baseRoot(), world.headRoot()));
        secondRun = new TransformationDetector().detect(world.baseRoot(), world.headRoot());
    }

    @Then("the change {string} is detected")
    public void the_change_is_detected(String title) {
        assertThat(new ChangeGrouper().group(transformations())).extracting(change -> change.title()).contains(title);
    }

    @Then("no removal, move or addition of {string} is reported")
    public void no_removal_move_or_addition_is_reported(String name) {
        assertThat(transformations()).filteredOn(t -> REMOVE_MOVE_ADD.contains(t.kind()))
                .noneMatch(t -> t.involvedDescriptions().stream().anyMatch(d -> d.endsWith("#" + name)));
    }

    @Then("no pull-up is detected")
    public void no_pull_up_is_detected() {
        assertThat(transformations()).extracting(DetectedTransformation::kind)
                .doesNotContain(TransformationKind.PULL_UP_FIELD, TransformationKind.PULL_UP_SYMBOL);
    }

    @Then("both runs report the same transformations in the same order")
    public void both_runs_report_the_same() {
        assertThat(secondRun).extracting(DetectedTransformation::toString)
                .containsExactlyElementsOf(transformations().stream().map(DetectedTransformation::toString).toList());
    }

    private void baseDeclares(List<String> classNames, String name, String type, boolean isMethod) {
        classes = classNames;
        member = name;
        memberType = type;
        method = isMethod;
        for (String className : classNames) {
            write(world.baseRoot(), className, "public class " + className + " {\n" + memberSource() + "}\n");
        }
    }

    private void headPullsUp(String base, List<String> extending, List<String> unchanged) {
        write(world.headRoot(), base, "public abstract class " + base + " {\n" + memberSource() + "}\n");
        for (String subclass : extending) {
            write(world.headRoot(), subclass, "public class " + subclass + " extends " + base + " {\n}\n");
        }
        for (String subclass : unchanged) {
            write(world.headRoot(), subclass, "public class " + subclass + " {\n" + memberSource() + "}\n");
        }
    }

    private String memberSource() {
        return method
                ? "    public " + memberType + " " + member + "() {\n        return null;\n    }\n"
                : "    protected " + memberType + " " + member + ";\n";
    }

    private List<DetectedTransformation> transformations() {
        return world.transformations().orElseThrow();
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
