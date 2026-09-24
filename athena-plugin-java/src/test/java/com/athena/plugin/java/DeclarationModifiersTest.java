package com.athena.plugin.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A declaration's visibility and modifier keywords, compared between revisions (ticket #313). */
class DeclarationModifiersTest {

    @Test
    void identicalModifiersInAnyOrderAreNoChange() {
        assertThat(modifiersOf("public static final").describeChangeTo(modifiersOf("final public static"))).isEmpty();
    }

    @Test
    void anAddedKeywordIsListedWithAPlus() {
        assertThat(modifiersOf("").describeChangeTo(modifiersOf("final"))).contains("+final");
    }

    @Test
    void aRemovedKeywordIsListedWithAMinus() {
        assertThat(modifiersOf("static").describeChangeTo(modifiersOf(""))).contains("-static");
    }

    @Test
    void aVisibilityChangeNamesBothLevels() {
        assertThat(modifiersOf("protected").describeChangeTo(modifiersOf(""))).contains("protected -> package-private");
    }

    @Test
    void visibilityComesFirstThenAddedThenRemovedKeywords() {
        assertThat(modifiersOf("public synchronized").describeChangeTo(modifiersOf("private static final")))
                .contains("public -> private, +final, +static, -synchronized");
    }

    @Test
    void untrackedKeywordsAreIgnored() {
        assertThat(modifiersOf("public").describeChangeTo(modifiersOf("public native"))).isEmpty();
    }

    private static DeclarationModifiers modifiersOf(String modifiers) {
        MethodDeclaration method = new JavaParser(JavaParserConfigurations.currentJava())
                .parseMethodDeclaration(modifiers + " void m() { }").getResult().orElseThrow();
        return DeclarationModifiers.of(method);
    }
}
