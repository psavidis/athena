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

    // ---- switch (ticket #289) ----

    @Test
    void anIfChainRewrittenAsASwitchWithTheSameConditionsIsDescribedAsSuch() {
        ControlFlow ifChain = controlFlowOf("if (s == A) { a(); } else if (s == B) { b(); } else { c(); }");
        ControlFlow switchStatement = controlFlowOf("switch (s) { case A: a(); break; case B: b(); break; default: c(); }");

        assertThat(ifChain.describeChangeTo(switchStatement)).contains("if-chain rewritten as switch");
    }

    @Test
    void aSwitchRewrittenAsAnIfChainIsDescribedAsSuch() {
        ControlFlow switchStatement = controlFlowOf("switch (s) { case A: a(); break; default: c(); }");
        ControlFlow ifChain = controlFlowOf("if (s == A) { a(); } else { c(); }");

        assertThat(switchStatement.describeChangeTo(ifChain)).contains("switch rewritten as if-chain");
    }

    @Test
    void fallThroughCasesAreOneBranchMatchingAnOrCondition() {
        ControlFlow ifChain = controlFlowOf("if (s == A || s == B) { a(); } else { c(); }");
        ControlFlow switchStatement = controlFlowOf("switch (s) { case A: case B: a(); break; default: c(); }");

        assertThat(ifChain.describeChangeTo(switchStatement)).contains("if-chain rewritten as switch");
    }

    @Test
    void aMultiLabelArrowCaseMatchesAnOrCondition() {
        ControlFlow ifChain = controlFlowOf("if (s == A || s == B) { a(); } else { c(); }");
        ControlFlow switchStatement = controlFlowOf("switch (s) { case A, B -> a(); default -> c(); }");

        assertThat(ifChain.describeChangeTo(switchStatement)).contains("if-chain rewritten as switch");
    }

    @Test
    void anIfChainRewrittenAsASwitchWithDifferentConditionsIsAChangedCondition() {
        ControlFlow ifChain = controlFlowOf("if (s == A) { a(); } else { c(); }");
        ControlFlow switchStatement = controlFlowOf("switch (s) { case B: a(); break; default: c(); }");

        assertThat(ifChain.describeChangeTo(switchStatement)).contains("condition changed");
    }

    @Test
    void aStatementAfterAnIfChainBecomingTheSwitchDefaultIsStillARewrite() {
        ControlFlow ifChain = controlFlowOf("if (s == A) { return a; } else if (s == B) { return b; } throw error();");
        ControlFlow switchStatement = controlFlowOf("switch (s) { case A: return a; case B: return b; default: throw error(); }");

        assertThat(ifChain.describeChangeTo(switchStatement)).contains("if-chain rewritten as switch");
    }

    @Test
    void anAddedCaseIsAnAddedBranch() {
        assertThat(controlFlowOf("switch (s) { case A: a(); break; default: c(); }")
                .describeChangeTo(controlFlowOf("switch (s) { case A: a(); break; case B: b(); break; default: c(); }")))
                .contains("branch added");
    }

    @Test
    void aRemovedCaseInASwitchExpressionIsARemovedBranch() {
        assertThat(controlFlowOf("int x = switch (s) { case A -> 1; case B -> 2; default -> 0; };")
                .describeChangeTo(controlFlowOf("int x = switch (s) { case A -> 1; default -> 0; };")))
                .contains("branch removed");
    }

    @Test
    void aChangedCaseLabelIsAChangedCondition() {
        assertThat(controlFlowOf("switch (s) { case A: a(); break; default: c(); }")
                .describeChangeTo(controlFlowOf("switch (s) { case B: a(); break; default: c(); }")))
                .contains("condition changed");
    }

    @Test
    void aChangedCaseBodyIsNoControlFlowChange() {
        assertThat(controlFlowOf("switch (s) { case A: a(); break; default: c(); }")
                .describeChangeTo(controlFlowOf("switch (s) { case A: b(); break; default: c(); }")))
                .isEmpty();
    }

    @Test
    void aLoneAddedBranchIsNotARestructuring() {
        assertThat(controlFlowOf("size--;").restructures(controlFlowOf("if (removed) { return; } size--;"))).isFalse();
    }

    @Test
    void aLoneChangedConditionIsNotARestructuring() {
        assertThat(controlFlowOf("if (a) { x(); }").restructures(controlFlowOf("if (a && b) { x(); }"))).isFalse();
    }

    @Test
    void aLoneRemovedLoopIsNotARestructuring() {
        assertThat(controlFlowOf("for (int i = 0; i < n; i++) { x(); }").restructures(controlFlowOf("x();"))).isFalse();
    }

    @Test
    void severalKindsOfChangeTogetherAreARestructuring() {
        assertThat(controlFlowOf("if (a) { x(); }").restructures(controlFlowOf("if (b) { x(); } if (c) { x(); }"))).isTrue();
    }

    @Test
    void anIfChainRewrittenAsASwitchIsARestructuring() {
        assertThat(controlFlowOf("if (s == A) { x(); } else if (s == B) { y(); } else { z(); }")
                .restructures(controlFlowOf("switch (s) { case A: x(); break; case B: y(); break; default: z(); }"))).isTrue();
    }

    @Test
    void identicalBodiesAreNotARestructuring() {
        assertThat(controlFlowOf("if (a) { x(); }").restructures(controlFlowOf("if (a) { x(); }"))).isFalse();
    }

    private static ControlFlow controlFlowOf(String statements) {
        MethodDeclaration method = new JavaParser(JavaParserConfigurations.currentJava())
                .parseMethodDeclaration("void m() { " + statements + " }").getResult().orElseThrow();
        return ControlFlow.of(method);
    }
}
