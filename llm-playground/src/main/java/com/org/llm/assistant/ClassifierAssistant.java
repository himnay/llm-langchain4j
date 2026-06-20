package com.org.llm.assistant;

import com.org.llm.model.Sentiment;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Text classification via {@code AiServices}: returning an {@code enum} directly makes
 * LangChain4j constrain the model's reply to one of the enum constants and parse it back —
 * no JSON, no parsing code, no prompt engineering for the output format.
 */
public interface ClassifierAssistant {

    @UserMessage("Classify the overall sentiment of this text: {{text}}")
    Sentiment classifySentiment(@V("text") String text);
}
