package com.org.llm.assistant;

import com.org.llm.model.ExtractedPerson;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Structured data extraction via {@code AiServices}: {@link ExtractedPerson} (a record with a
 * nested {@code List<String>}) is reflected into a JSON schema automatically — no hand-rolled
 * schema, no manual JSON parsing.
 */
public interface ExtractionAssistant {

    @UserMessage("""
            Extract the person's details from the text below. Use null for fields not mentioned,
            and an empty list for interests if none are mentioned.

            Text: {{text}}
            """)
    ExtractedPerson extract(@V("text") String text);
}
