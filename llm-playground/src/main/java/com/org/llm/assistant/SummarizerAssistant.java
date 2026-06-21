package com.org.llm.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Plain-text summarization with a parameterized length constraint via {@code @SystemMessage}.
 */
public interface SummarizerAssistant {

    @SystemMessage("You are a concise summarizer. Summarize the user's text in at most {{maxSentences}} sentences.")
    @UserMessage("{{text}}")
    String summarize(@V("text") String text, @V("maxSentences") int maxSentences);
}
