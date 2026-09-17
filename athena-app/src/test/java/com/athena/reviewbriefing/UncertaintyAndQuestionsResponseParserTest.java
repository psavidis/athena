package com.athena.reviewbriefing;

import com.athena.ai.AiProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link UncertaintyAndQuestionsResponseParser} (ticket #221). */
class UncertaintyAndQuestionsResponseParserTest {

    @Test
    void parsesPlainJson() {
        String text = """
                {"uncertainties": [{"description": "Why was this renamed?", "entity": "Greeter"}], \
                "questions": [{"description": "Was this part of a refactor?", "entity": "Greeter"}]}""";

        UncertaintyAndQuestions result = UncertaintyAndQuestionsResponseParser.parse(text);

        assertThat(result.uncertainties()).hasSize(1);
        assertThat(result.uncertainties().get(0).description()).isEqualTo("Why was this renamed?");
        assertThat(result.uncertainties().get(0).entityReference()).contains("Greeter");
        assertThat(result.questions()).hasSize(1);
    }

    @Test
    void parsesJsonWrappedInAMarkdownCodeFenceDespiteBeingAskedNotTo() {
        String text = """
                ```json
                {"uncertainties": [], "questions": []}
                ```""";

        UncertaintyAndQuestions result = UncertaintyAndQuestionsResponseParser.parse(text);

        assertThat(result.uncertainties()).isEmpty();
        assertThat(result.questions()).isEmpty();
    }

    @Test
    void parsesJsonWrappedInABareCodeFenceWithNoLanguageTag() {
        String text = """
                ```
                {"uncertainties": [], "questions": []}
                ```""";

        UncertaintyAndQuestions result = UncertaintyAndQuestionsResponseParser.parse(text);

        assertThat(result.uncertainties()).isEmpty();
        assertThat(result.questions()).isEmpty();
    }

    @Test
    void skipsAnItemWithABlankDescription() {
        String text = """
                {"uncertainties": [{"description": "", "entity": "Greeter"}], "questions": []}""";

        UncertaintyAndQuestions result = UncertaintyAndQuestionsResponseParser.parse(text);

        assertThat(result.uncertainties()).isEmpty();
    }

    @Test
    void treatsAnEmptyEntityAsNoEntityReference() {
        String text = """
                {"uncertainties": [{"description": "Unclear intent", "entity": ""}], "questions": []}""";

        UncertaintyAndQuestions result = UncertaintyAndQuestionsResponseParser.parse(text);

        assertThat(result.uncertainties().get(0).entityReference()).isEmpty();
    }

    @Test
    void rejectsTrulyUnparsableText() {
        assertThatThrownBy(() -> UncertaintyAndQuestionsResponseParser.parse("not json at all"))
                .isInstanceOf(AiProviderException.class);
    }
}
