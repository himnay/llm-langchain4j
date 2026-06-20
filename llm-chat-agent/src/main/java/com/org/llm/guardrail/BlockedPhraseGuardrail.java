package com.org.llm.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Blocks requests containing forbidden phrases before any model call — replaces Spring AI's
 * {@code SafeGuardAdvisor}. LangChain4j has no advisor-order concept; an {@code InputGuardrail}
 * always runs before the request reaches the model, so it occupies the same "first line of
 * defense" position {@code SafeGuardAdvisor.builder().order(Integer.MIN_VALUE)} did.
 */
@Component
public class BlockedPhraseGuardrail implements InputGuardrail {

    private static final List<String> DEFAULT_BLOCKED_PHRASES =
            List.of("ignore previous instructions", "jailbreak", "prompt injection");

    private final List<String> blockedPhrases;

    public BlockedPhraseGuardrail(@Value("${app.guardrail.blocked-words:}") String extraBlockedWords) {
        this.blockedPhrases = new ArrayList<>(DEFAULT_BLOCKED_PHRASES);
        if (extraBlockedWords != null && !extraBlockedWords.isBlank()) {
            Arrays.stream(extraBlockedWords.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .forEach(blockedPhrases::add);
        }
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText().toLowerCase(Locale.ROOT);
        for (String phrase : blockedPhrases) {
            if (text.contains(phrase)) {
                return failure("Request blocked: contains forbidden phrase \"" + phrase + "\"");
            }
        }
        return success();
    }
}
