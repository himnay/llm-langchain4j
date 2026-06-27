package com.org.llm.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Blocks requests containing prompt-injection patterns before any model call — the LangChain4j
 * equivalent of Spring AI's {@code SafeGuardAdvisor}. An {@code InputGuardrail} always runs
 * before the request reaches the model, so this is the first line of defense.
 *
 * <p>Patterns are externalised in {@link InjectionGuardProperties}
 * ({@code app.security.injection-guard.patterns}) so new attack signatures can be added in
 * configuration without code changes. Regex matching is used (vs. the prior substring approach)
 * to cover spacing variants, word boundaries, and combined attacks.</p>
 */
@Slf4j
@Component
public class BlockedPhraseGuardrail implements InputGuardrail {

    private final List<Pattern> compiledPatterns;
    private final boolean enabled;
    private final String blockMessage;

    public BlockedPhraseGuardrail(InjectionGuardProperties properties) {
        this.enabled = properties.isEnabled();
        this.blockMessage = properties.getBlockMessage();
        this.compiledPatterns = properties.getPatterns().stream()
                .flatMap(regex -> {
                    try {
                        return java.util.stream.Stream.of(Pattern.compile(regex));
                    } catch (PatternSyntaxException ex) {
                        log.error("SECURITY | invalid injection pattern skipped | regex='{}' | error={}", regex, ex.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
        log.info("SECURITY | BlockedPhraseGuardrail ready | patterns={} enabled={}", compiledPatterns.size(), enabled);
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (!enabled) {
            return success();
        }
        String text = userMessage.singleText();
        if (text == null || text.isBlank()) {
            return success();
        }
        for (Pattern pattern : compiledPatterns) {
            if (pattern.matcher(text).find()) {
                log.warn("SECURITY | Injection pattern matched in user message | pattern='{}'", pattern.pattern());
                return failure(blockMessage);
            }
        }
        return success();
    }
}
