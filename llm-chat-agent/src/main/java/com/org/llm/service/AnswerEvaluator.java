package com.org.llm.service;

import com.org.llm.assistant.FaithfulnessJudge;
import com.org.llm.assistant.FaithfulnessVerdict;
import dev.langchain4j.rag.content.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Faithfulness/groundedness check for RAG answers. LangChain4j ships no built-in evaluator
 * equivalent to Spring AI's {@code FactCheckingEvaluator}, so this delegates to
 * {@link FaithfulnessJudge}, a custom LLM-as-judge {@code AiServices} interface. Gated behind
 * {@code app.rag.evaluate-faithfulness} (default {@code false}) since it costs one extra LLM call
 * per request; only worth running when there's actually RAG context to check against.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerEvaluator {

    private final FaithfulnessJudge faithfulnessJudge;

    /**
     * Returns {@code true}/{@code false} for whether {@code answer} is grounded in
     * {@code context}, or {@code true} (fail-open) if the judge call itself errors.
     */
    public boolean isFaithful(String question, List<Content> context, String answer) {
        try {
            String contextText = context.stream()
                    .map(content -> content.textSegment().text())
                    .collect(Collectors.joining("\n\n"));
            FaithfulnessVerdict verdict = faithfulnessJudge.check(question, contextText, answer);
            log.debug("FactCheck: {} | question='{}' | reasoning='{}'",
                    verdict.pass() ? "PASS" : "FAIL", question, verdict.reasoning());
            return verdict.pass();
        } catch (Exception e) {
            log.warn("FaithfulnessJudge failed ({}); defaulting to faithful=true", e.getMessage());
            return true;
        }
    }
}
