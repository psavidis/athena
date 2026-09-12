package com.athena.semantic;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeIdentitySteps {

    private Change changeA;
    private Change changeB;
    private ChangeIdentity identityA;
    private ChangeIdentity identityB;
    private List<Change> revisionASet;
    private List<Change> revisionBSet;
    private Optional<Change> lookupResult;
    private ChangeEvidence evidence;

    @Given("a Change representing a rename from {string} to {string}")
    public void a_change_representing_a_rename(String oldSymbol, String newSymbol) {
        changeA = renameChange(oldSymbol, newSymbol, List.of());
    }

    @Given("a Change representing a rename from {string} to {string} detected in revision A")
    public void a_change_representing_a_rename_revision_a(String oldSymbol, String newSymbol) {
        changeA = renameChange(oldSymbol, newSymbol, List.of());
    }

    @Given("the same conceptual rename detected again in revision B after history was rewritten")
    public void the_same_conceptual_rename_revision_b() {
        changeB = renameChange("User#login", "Account#login", List.of());
    }

    @Given("a Change representing a move of {string} to {string}")
    public void a_change_representing_a_move(String oldSymbol, String newSymbol) {
        changeB = moveChange(oldSymbol, newSymbol);
    }

    @Given("a set of Changes computed from revision A, including a rename from {string} to {string}")
    public void a_set_of_changes_revision_a(String oldSymbol, String newSymbol) {
        revisionASet = new ArrayList<>();
        revisionASet.add(renameChange(oldSymbol, newSymbol, List.of()));
    }

    @Given("a set of Changes computed from revision B, including the same conceptual rename")
    public void a_set_of_changes_revision_b() {
        revisionBSet = new ArrayList<>();
        revisionBSet.add(renameChange("User#login", "Account#login", List.of()));
    }

    @Given("a Change representing a rename from {string} to {string} touching file {string} with diff {string}")
    public void a_change_with_evidence(String oldSymbol, String newSymbol, String file, String diff) {
        changeA = renameChangeWithDiff(oldSymbol, newSymbol, file, diff);
    }

    @When("the semantic engine computes the Change's identity key")
    public void compute_identity_key() {
        identityA = ChangeIdentity.of(changeA);
    }

    @When("the semantic engine computes both Changes' identity keys")
    public void compute_both_identity_keys() {
        identityA = ChangeIdentity.of(changeA);
        identityB = ChangeIdentity.of(changeB);
    }

    @When("the semantic engine looks up the revision-A Change's identity key in the revision-B set")
    public void look_up_identity_in_other_set() {
        ChangeIdentity key = ChangeIdentity.of(revisionASet.get(0));
        lookupResult = ChangeIdentity.findMatching(key, revisionBSet);
    }

    @When("someone inspects the Change's evidence")
    public void inspect_evidence() {
        evidence = ChangeEvidence.of(changeA);
    }

    @Then("the identity key reflects the rename transformation shape")
    public void identity_key_reflects_rename_shape() {
        assertThat(identityA.transformationShape()).isEqualTo(TransformationKind.RENAME_SYMBOL);
    }

    @Then("the identity key reflects the {string} and {string} symbols")
    public void identity_key_reflects_symbols(String symbolA, String symbolB) {
        assertThat(identityA.symbolSet()).contains(symbolA, symbolB);
    }

    @Then("both identity keys are equal")
    public void both_identity_keys_are_equal() {
        assertThat(identityA).isEqualTo(identityB);
    }

    @Then("the identity keys are different")
    public void identity_keys_are_different() {
        assertThat(identityA).isNotEqualTo(identityB);
    }

    @Then("the matching revision-B Change is found")
    public void the_matching_change_is_found() {
        assertThat(lookupResult).isPresent();
        assertThat(lookupResult.get().title()).isEqualTo(revisionBSet.get(0).title());
    }

    @Then("the evidence includes the file {string}")
    public void the_evidence_includes_the_file(String file) {
        assertThat(evidence.files()).contains(file);
    }

    @And("the evidence includes the symbols {string} and {string}")
    public void the_evidence_includes_the_symbols(String symbolA, String symbolB) {
        assertThat(evidence.symbols()).contains(symbolA, symbolB);
    }

    @And("the evidence includes the underlying textual diff")
    public void the_evidence_includes_the_diff() {
        assertThat(evidence.textualDiff()).isNotBlank();
    }

    private Change renameChange(String oldSymbol, String newSymbol, List<String> files) {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.RENAME_SYMBOL,
                List.of(oldSymbol, newSymbol), files);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private Change renameChangeWithDiff(String oldSymbol, String newSymbol, String file, String diff) {
        DetectedTransformation t = DetectedTransformation.withDiff(TransformationKind.RENAME_SYMBOL,
                List.of(oldSymbol, newSymbol), List.of(file), diff);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private Change moveChange(String oldSymbol, String newSymbol) {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.MOVE_SYMBOL,
                List.of(oldSymbol, newSymbol), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
