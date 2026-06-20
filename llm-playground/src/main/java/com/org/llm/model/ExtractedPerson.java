package com.org.llm.model;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/** Structured extraction target — nested records/lists are derived into the JSON schema too. */
public record ExtractedPerson(
        @Description("the person's full name") String name,
        @Description("the person's age in years, or null if not mentioned") Integer age,
        @Description("the person's occupation, or null if not mentioned") String occupation,
        @Description("topics or interests the person is associated with, empty if none mentioned") List<String> interests
) {
}
