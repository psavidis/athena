package com.athena.plugin.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A method body's control flow — its conditions, branches and loops — compared between
 * two revisions to tell whether an edit may change runtime behavior (tickets #18, #263).
 */
class ControlFlowTest {

    @Test
    void identicalBodiesHaveNoControlFlowChange() {
        ControlFlow body = controlFlowOf("if (a) { x(); }");

        assertThat(body.describeChangeTo(controlFlowOf("if (a) { x(); }"))).isEmpty();
    }

    @Test
    void aChangedConditionIsDescribedAsSuch() {
        assertThat(controlFlowOf("if (a) { x(); }").describeChangeTo(controlFlowOf("if (a && b) { x(); }")))
                .contains("condition changed");
    }

    @Test
    void anAddedGuardIsAnAddedBranchNotJustAChangedCondition() {
        assertThat(controlFlowOf("size--;").describeChangeTo(controlFlowOf("if (removed) { return; } size--;")))
                .contains("branch added");
    }

    @Test
    void anAddedElseIsAnAddedBranch() {
        assertThat(controlFlowOf("if (a) { x(); }").describeChangeTo(controlFlowOf("if (a) { x(); } else { y(); }")))
                .contains("branch added");
    }

    @Test
    void aRemovedBranchIsDescribedAsRemoved() {
        assertThat(controlFlowOf("if (a) { x(); } else { y(); }").describeChangeTo(controlFlowOf("if (a) { x(); }")))
                .contains("branch removed");
    }

    @Test
    void anIfTurnedIntoAWhileReportsBothAspects() {
        assertThat(controlFlowOf("if (a) { x(); }").describeChangeTo(controlFlowOf("while (a) { x(); }")))
                .contains("branch removed, loop added");
    }

    @Test
    void anAddedLoopIsDescribedAsSuch() {
        assertThat(controlFlowOf("x();").describeChangeTo(controlFlowOf("for (int i = 0; i < 3; i++) { x(); }")))
                .contains("loop added");
    }

    @Test
    void aChangedCallOrReturnValueIsNotAControlFlowChange() {
        assertThat(controlFlowOf("return formatA();").describeChangeTo(controlFlowOf("return formatB();"))).isEmpty();
    }

    @Test
    void aMethodWithoutABodyHasNoControlFlow() {
        MethodDeclaration abstractMethod = new JavaParser(JavaParserConfigurations.currentJava())
                .parseMethodDeclaration("abstract void m();").getResult().orElseThrow();

        assertThat(ControlFlow.of(abstractMethod).describeChangeTo(controlFlowOf("x();"))).isEmpty();
    }

    private static ControlFlow controlFlowOf(String statements) {
        MethodDeclaration method = new JavaParser(JavaParserConfigurations.currentJava())
                .parseMethodDeclaration("void m() { " + statements + " }").getResult().orElseThrow();
        return ControlFlow.of(method);
    }
}
