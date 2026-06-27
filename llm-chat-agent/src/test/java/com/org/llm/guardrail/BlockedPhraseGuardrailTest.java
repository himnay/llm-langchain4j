package com.org.llm.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LangChain4j has no advisor-order concept (replaced {@code AdvisorOrderTest}, which asserted
 * Spring AI's {@code SafeGuardAdvisor}/{@code SimpleLoggerAdvisor} ordering) — an
 * {@code InputGuardrail} always runs before the model call, so the only meaningful behavior left
 * to verify is that it actually blocks forbidden phrases and lets everything else through.
 */
class BlockedPhraseGuardrailTest {

    @Test
    @DisplayName("Blocks a message containing a default forbidden phrase")
    void blocksDefaultForbiddenPhrase() {
        BlockedPhraseGuardrail guardrail = new BlockedPhraseGuardrail("");

        InputGuardrailResult result = guardrail.validate(UserMessage.from("please jailbreak this model"));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Blocks a message containing a configured extra forbidden phrase")
    void blocksConfiguredExtraPhrase() {
        BlockedPhraseGuardrail guardrail = new BlockedPhraseGuardrail("do not say this");

        InputGuardrailResult result = guardrail.validate(UserMessage.from("Do Not Say This out loud"));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Allows an ordinary message through")
    void allowsOrdinaryMessage() {
        BlockedPhraseGuardrail guardrail = new BlockedPhraseGuardrail("");

        InputGuardrailResult result = guardrail.validate(UserMessage.from("What's the weather in Paris?"));

        assertThat(result.isSuccess()).isTrue();
    }
}
