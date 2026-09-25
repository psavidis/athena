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
        assertThat(describe("audit(t); return t;", "audit(\"x\"); return t;")).isEqualTo("audit(…): t -> \"x\"");
    }

    // Ticket #361: a call whose only change is its arguments is named with that change.

    @Test
    void aChangedArgumentIsTrimmedToThePartThatDiffers() {
        assertThat(describe("validateMember(memberId, group.id(), \"offset-commit\"); return t;",
                "validateMember(memberId, group.id(), \"txn-offset-commit\"); return t;"))
                .isEqualTo("validateMember(…): \"offset-commit\" -> \"txn-offset-commit\"");
    }

    @Test
    void aChangedArgumentInsideABlockIsNamedToo() {
        assertThat(describe("if (a) { audit(t, 1); } return t;", "if (a) { audit(t, 2); } return t;"))
                .isEqualTo("audit(…): 1 -> 2");
    }

    @Test
    void aCallWithAReceiverIsNamedWithIt() {
        assertThat(describe("offsets.put(k, 1); return t;", "offsets.put(k, 2); return t;")).isEqualTo("offsets.put(…): 1 -> 2");
    }

    @Test
    void swappedConditionalBranchesInAnArgumentAreASwap() {
        assertThat(describe("validateMember(m, tx ? \"a\" : \"b\"); return t;", "validateMember(m, tx ? \"b\" : \"a\"); return t;"))
                .isEqualTo("swapped ?: branches in validateMember(…)");
    }

    @Test
    void swappedConditionalBranchesInAReturnAreASwap() {
        assertThat(describe("return tx ? a : b;", "return tx ? b : a;")).isEqualTo("swapped ?: branches in return");
    }

    @Test
    void aChangedArgumentCountReadsAsChangedArguments() {
        assertThat(describe("record(e); return t;", "record(e, u); return t;")).isEqualTo("arguments of record changed");
    }

    @Test
    void aLongChangedArgumentReadsAsChangedArguments() {
        assertThat(describe("record(theFirstVeryLongIdentifierThatGoesOnAndOn); return t;",
                "record(theSecondVeryLongIdentifierThatGoesOnAndOn); return t;"))
                .isEqualTo("arguments of record changed");
    }

    @Test
    void severalChangedArgumentsOfOneCallReadAsChangedArguments() {
        assertThat(describe("record(a, b); return t;", "record(c, d); return t;")).isEqualTo("arguments of record changed");
    }

    @Test
    void theInnermostCallWithAChangedArgumentIsNamed() {
        assertThat(describe("send(wrap(1)); return t;", "send(wrap(2)); return t;")).isEqualTo("wrap(…): 1 -> 2");
    }

    @Test
    void aStatementThatChangedBeyondTheArgumentsIsNotNamed() {
        assertThat(describe("int c = record(e); return t;", "long c = record(u); return t;")).isEqualTo("other statements changed");
    }

    // Ticket #375: a receiver change shown as a call added and removed isn't repeated as an argument.

    @Test
    void anArgumentThatOnlyChangesAReceiverIsNotRepeated() {
        assertThat(describe("given(owners.findPetTypes()); return t;", "given(types.findPetTypes()); return t;"))
                .isEqualTo("+types.findPetTypes, -owners.findPetTypes");
    }

    @Test
    void aFieldReceiverChangeIsNotRepeatedEither() {
        assertThat(describe("given(this.pets.findPetTypes()); return t;", "given(types.findPetTypes()); return t;"))
                .isEqualTo("+types.findPetTypes, -pets.findPetTypes");
    }

    @Test
    void anArgumentThatChangesBeyondTheReceiverIsStillListed() {
        assertThat(describe("given(owners.find(1)); return t;", "given(types.find(2)); return t;"))
                .isEqualTo("+types.find, -owners.find, types.find(…): 1 -> 2");
    }

    @Test
    void aPlainArgumentChangeIsStillListed() {
        assertThat(describe("given(owners); return t;", "given(types); return t;")).isEqualTo("given(…): owners -> types");
    }

    @Test
    void theSameArgumentChangeTwiceIsCounted() {
        assertThat(describe("record(\"a\"); x = 1; record(\"a\"); return t;", "record(\"b\"); x = 1; record(\"b\"); return t;"))
                .isEqualTo("record(…): \"a\" -> \"b\" ×2");
    }

    @Test
    void aCallOnAMethodResultIsNamedBySimpleName() {
        assertThat(describe("return t;", "getContext().trigger(t); return t;")).isEqualTo("+getContext, +trigger");
    }

    // Ticket #360: a call on a plain name or a field of this class is named with its receiver.

    @Test
    void aCallOnAPlainNameIsNamedWithIt() {
        assertThat(describe("return t;", "consumedOffsets.clear(); return t;")).isEqualTo("+consumedOffsets.clear");
    }

    @Test
    void aCallOnAFieldOfThisIsNamedWithTheField() {
        assertThat(describe("return t;", "this.cache.invalidate(); return t;")).isEqualTo("+cache.invalidate");
    }

    @Test
    void aChainIsNamedWithItsFirstReceiverOnly() {
        assertThat(describe("return t;", "session.getContext().trigger(t); return t;"))
                .isEqualTo("+session.getContext, +trigger");
    }

    @Test
    void callsOnSuperATypeOrAQualifiedNameKeepTheSimpleName() {
        assertThat(describe("return t;", "super.start(); Objects.hash(t); java.util.Objects.requireNonNull(t); return t;"))
                .isEqualTo("+start, +hash, +requireNonNull");
    }

    @Test
    void theSameMethodOnTwoReceiversIsTwoItems() {
        assertThat(describe("return t;", "in.clear(); out.clear(); return t;")).isEqualTo("+in.clear, +out.clear");
    }

    @Test
    void aCallMovedToAnotherReceiverIsAddedAndRemoved() {
        assertThat(describe("a.clear(); return t;", "b.clear(); return t;")).isEqualTo("+b.clear, -a.clear");
    }

    @Test
    void theSameMethodsOnAnotherReceiverAreStillTheSameCalls() {
        assertThat(summaryOf("for (String p : ps) { out.write(p); } return t;")
                .makesSameCallsAs(summaryOf("Writer w = out; int i = 0; while (i < 2) { w.write(ps[i]); i++; } return t;")))
                .isTrue();
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
        assertThat(describe("return name;", "return name.trim();")).isEqualTo("+name.trim, return name -> name.trim()");
    }

    @Test
    void aReturnInsideALambdaIsNotTheMethodsReturn() {
        assertThat(describe("Supplier<Object> s = () -> { return a; }; return t;",
                "Supplier<Object> s = () -> { return b; }; return t;")).isEqualTo("other statements changed");
    }

    @Test
    void aReturnInsideAnAnonymousClassIsNotTheMethodsReturn() {
        assertThat(describe("Runnable r = new Runnable() { public void run() { } Object v() { return a; } }; return t;",
                "Runnable r = new Runnable() { public void run() { } Object v() { return b; } }; return t;"))
                .isEqualTo("other statements changed");
    }

    @Test
    void aChangedReturnedAnonymousClassIsAChangedValue() {
        assertThat(describe("return new Object() { public String toString() { return \"a\"; } };",
                "return new Object() { public String describe() { return \"a\"; } };")).isEqualTo("return value changed");
    }

    @Test
    void aChangedPartContainingALambdaIsAChangedValue() {
        assertThat(describe("return java.util.stream.Stream.of(t).filter(x -> x != null).count();",
                "return java.util.stream.Stream.of(t).filter(java.util.Objects::nonNull).count();")).isEqualTo("return value changed");
    }

    @Test
    void aPartThatEndsInAnOperatorIsAChangedValue() {
        assertThat(describe("return t != null ? t.port : port;", "return ref == null ? 0 : ref.get().port;"))
                .isEqualTo("+ref.get, return value changed");
    }

    @Test
    void aValueWrappedInNewCodeIsAChangedValueNotNothing() {
        assertThat(describe("return size();", "return ref == null ? 0 : ref.get().size();")).isEqualTo("+ref.get, return value changed");
    }

    @Test
    void aWholeChangedOperandIsStillNamed() {
        assertThat(describe("return f(a, t);", "return f(b, t);")).isEqualTo("return a -> b");
    }

    @Test
    void identicalItemsAreListedOnceWithACount() {
        assertThat(describe("if (a > 0) { return f(a, t); } return f(a, t);", "if (a > 0) { return f(b, t); } return f(b, t);"))
                .isEqualTo("return a -> b ×2");
    }

    @Test
    void differentItemsAreEachListedInOrder() {
        assertThat(describe("if (a > 0) { return f(a, t); } return g(a, t);", "if (a > 0) { return f(b, t); } return g(b, t);"))
                .isEqualTo("return a -> b ×2");
        assertThat(describe("if (a > 0) { return f(a, t); } return g(t);", "if (a > 0) { return f(b, t); } return g(null);"))
                .isEqualTo("return a -> b, return t -> null");
    }

    @Test
    void aCountedItemTakesOneSlotTowardTheLimit() {
        assertThat(describe("if (a > 0) { return f(a); } return f(a);",
                "p(); q(); r(); s(); if (a > 0) { return f(b); } return f(b);"))
                .isEqualTo("+p, +q, +r, +s, return a -> b ×2");
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
