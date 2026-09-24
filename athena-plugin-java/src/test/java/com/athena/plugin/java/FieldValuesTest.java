package com.athena.plugin.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** What changed in a field initializer (ticket #316). */
class FieldValuesTest {

    @Test
    void namesAnElementAddedToAnArrayInitializerBySimpleName() {
        assertThat(describe("new T[] { T.A, T.C }", "new T[] { T.A, T.B, T.C }")).isEqualTo("+B");
    }

    @Test
    void namesElementsAddedAndRemovedFromACollectionFactory() {
        assertThat(describe("List.of(\"a\", \"b\")", "List.of(\"b\", \"c\")")).isEqualTo("+\"c\", -\"a\"");
    }

    @Test
    void saysWhenOnlyTheOrderChanged() {
        assertThat(describe("Set.of(A, B)", "Set.of(B, A)")).isEqualTo("elements reordered");
    }

    @Test
    void showsShortScalarValuesOnBothSides() {
        assertThat(describe("100", "200")).isEqualTo("100 -> 200");
    }

    @Test
    void summarizesLongScalarValues() {
        assertThat(describe("compute(\"a very long argument that goes on and on\")", "compute(\"another very long argument here\")"))
                .isEqualTo("value changed");
    }

    @Test
    void describesAnAddedInitializer() {
        assertThat(FieldValues.describeChange(Optional.empty(), Optional.of(parse("10")))).isEqualTo("none -> 10");
    }

    @Test
    void listsAtMostFiveElements() {
        assertThat(describe("List.of()", "List.of(a, b, c, d, e, f, g)")).isEqualTo("+a, +b, +c, +d, +e …and 2 more");
    }

    private static String describe(String base, String head) {
        return FieldValues.describeChange(Optional.of(parse(base)), Optional.of(parse(head)));
    }

    private static Expression parse(String expression) {
        return new JavaParser(JavaParserConfigurations.currentJava()).parseExpression(expression).getResult().orElseThrow();
    }
}
