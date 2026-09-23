package com.athena.plugin.java;

import com.github.javaparser.ParserConfiguration;

/**
 * The one JavaParser configuration every parse in this plugin starts from, so the
 * parse check that decides a file's analysis status and the detectors that read the
 * same file never disagree about which Java syntax is valid (ticket #262).
 */
public final class JavaParserConfigurations {

    private JavaParserConfigurations() {
    }

    /**
     * A fresh configuration accepting the newest Java syntax JavaParser supports. Real
     * projects already ship Java 21+ constructs (pattern-matching switches, record
     * patterns, unnamed variables); a file that fails to parse drops out of the review,
     * so the parser follows the language rather than a fixed older level. Fresh on every
     * call because {@link ParserConfiguration} is mutable (callers add a symbol resolver).
     */
    public static ParserConfiguration currentJava() {
        return new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }
}
