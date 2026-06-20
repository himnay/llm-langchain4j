package com.org.llm.assistant;

import dev.langchain4j.model.output.structured.Description;

/** Structured verdict returned by {@link FaithfulnessJudge} — LangChain4j derives the JSON schema from this record. */
public record FaithfulnessVerdict(
        @Description("true only if every claim in the answer is entailed by the context") boolean pass,
        @Description("one short sentence explaining the verdict") String reasoning
) {
}
