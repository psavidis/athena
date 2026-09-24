package com.athena.plugin.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a body edit changed inside a method — calls, thrown exception types and return
 * statements — so a body modification says more than "the body changed" (ticket #288).
 */
class BodySummaryTest {

    @Test
    void anAddedCallIsListedWithAPlus() {
        assertThat(describe("return t;", "trigger(t); return t;")).isEqualTo("+trigger");
    }

    @Test
    void aRemovedCallIsListedWithAMinus() {
        assertThat(describe("audit(t); return t;", "return t;")).isEqualTo("-audit");
    }

    @Test
    void aCallAddedTwiceIsListedOnce() {
        assertThat(describe("return t;", "log(t); log(t); return t;")).isEqualTo("+log");
    }

    @Test
    void anExtraCallToAnExistingMethodCountsAsAdded() {
        assertThat(describe("log(t); return t;", "log(t); log(t); return t;")).isEqualTo("+log");
    }

    @Test
    void aChangedArgumentIsNotACallAddedOrRemoved() {
        assertThat(describe("audit(t); return t;", "audit(\"x\"); return t;")).isEqualTo("other statements changed");
    }

    @Test
    void callsAreNamedBySimpleName() {
        assertThat(describe("return t;", "session.getContext().trigger(t); return t;")).isEqualTo("+getContext, +trigger");
    }

    @Test
    void anAddedThrowNamesTheExceptionType() {
        assertThat(describe("try { return t; } catch (RuntimeException e) { return t; }",
                "try { return t; } catch (RuntimeException e) { throw new IllegalStateException(e); }"))
                .isEqualTo("+throw IllegalStateException, return statements 2 → 1");
    }

    @Test
    void aRemovedThrowNamesTheExceptionType() {
        assertThat(describe("throw new IllegalStateException();", "return null;"))
                .isEqualTo("-throw IllegalStateException, return statements 0 → 1");
    }

    @Test
    void aRethrownVariableIsNamedByItsExpression() {
        assertThat(describe("return t;", "try { return t; } catch (RuntimeException e) { throw e; }"))
                .isEqualTo("+throw e");
    }

    @Test
    void aChangedReturnCountIsDescribed() {
        assertThat(describe("return t;", "try { return t; } catch (RuntimeException e) { return null; }"))
                .isEqualTo("return statements 1 → 2");
    }

    @Test
    void atMostFiveItemsAreListed() {
        assertThat(describe("return t;", "a(); b(); c(); d(); e(); f(); g(); return t;"))
                .isEqualTo("+a, +b, +c, +d, +e …and 2 more");
    }

    @Test
    void addedCallsComeBeforeRemovedCallsThrowsAndReturns() {
        assertThat(describe("old(); return t;", "try { fresh(); return t; } catch (RuntimeException e) { throw new X(); }"))
                .isEqualTo("+fresh, -old, +throw X");
    }

    @Test
    void anEditWithNoSuchDifferenceIsOtherStatements() {
        assertThat(describe("int x = a + b; return x;", "int x = a - b; return x;")).isEqualTo("other statements changed");
    }

    @Test
    void anUnrolledLoopMakesTheSameCalls() {
        assertThat(summaryOf("for (Object p : ps) { write(p); } return t;")
                .makesSameCallsAs(summaryOf("int i = 0; while (i < 2) { write(a); write(b); i++; } return t;"))).isTrue();
    }

    @Test
    void aNewCallIsNotTheSameCalls() {
        assertThat(summaryOf("write(t); return t;").makesSameCallsAs(summaryOf("write(t); log(t); return t;"))).isFalse();
    }

    @Test
    void throwingAnAlreadyThrownTypeMoreOftenIsNotTheSameCalls() {
        assertThat(summaryOf("if (k == 0) { throw new X(); } return t;")
                .makesSameCallsAs(summaryOf("if (t == null) { throw new X(); } if (k == 0) { throw new X(); } return t;")))
                .isFalse();
    }

    @Test
    void aNewThrowOrAnExtraReturnIsNotTheSameCalls() {
        assertThat(summaryOf("return t;").makesSameCallsAs(summaryOf("if (a == 0) { throw new X(); } return t;"))).isFalse();
        assertThat(summaryOf("return t;").makesSameCallsAs(summaryOf("if (a == 0) { return null; } return t;"))).isFalse();
    }

    @Test
    void aMovedStatementIsNamedAfterItsNewPredecessor() {
        assertThat(describe("modCount++; check(index); size++; return t;", "check(index); modCount++; size++; return t;"))
                .isEqualTo("moved modCount++ after check(index)");
    }

    @Test
    void aStatementMovedToTheTopIsNamedBeforeItsNewSuccessor() {
        assertThat(describe("a = 1; b = 2; check(); return t;", "check(); a = 1; b = 2; return t;"))
                .isEqualTo("moved check() before a = 1");
    }

    @Test
    void aChangedReturnedValueIsTrimmedToTheDifferingPart() {
        assertThat(describe("return a == 0 ? t : t.toString();", "return a == 0 ? null : t.toString();"))
                .isEqualTo("return t -> null");
    }

    @Test
    void aLongDifferingReturnValueIsSummarized() {
        assertThat(describe("return compute(\"a very long first argument\", t);", "return compute(\"an entirely different argument\", t, a, b);"))
                .isEqualTo("return value changed");
    }

    @Test
    void anInsertionIntoAReturnedValueIsAnchoredOnThePrecedingToken() {
        assertThat(describe("return name;", "return name.trim();")).isEqualTo("+trim, return name -> name.trim()");
    }

    private static String describe(String baseBody, String headBody) {
        return summaryOf(baseBody).describeChangeTo(summaryOf(headBody));
    }

    private static BodySummary summaryOf(String body) {
        MethodDeclaration method = new JavaParser(JavaParserConfigurations.currentJava())
                .parseMethodDeclaration("Object m(Object t, int a, int b) { " + body + " }").getResult().orElseThrow();
        return BodySummary.of(method);
    }
}
