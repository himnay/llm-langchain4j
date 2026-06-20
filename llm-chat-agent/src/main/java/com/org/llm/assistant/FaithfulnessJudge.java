package com.org.llm.assistant;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LLM-as-judge replacement for Spring AI's {@code FactCheckingEvaluator} — LangChain4j ships no
 * built-in RAG-answer evaluator, so this is a plain {@code AiServices} interface returning a
 * structured {@link FaithfulnessVerdict} instead of a hand-parsed pass/fail string.
 */
public interface FaithfulnessJudge {

    @UserMessage("""
            You are verifying whether an AI-generated ANSWER is fully supported by the CONTEXT.
            Set pass=true only if every claim in the ANSWER is entailed by the CONTEXT; otherwise
            set pass=false and explain which claim is unsupported.

            CONTEXT:
            {{context}}

            QUESTION:
            {{question}}

            ANSWER:
            {{answer}}
            """)
    FaithfulnessVerdict check(@V("question") String question, @V("context") String context, @V("answer") String answer);
}
