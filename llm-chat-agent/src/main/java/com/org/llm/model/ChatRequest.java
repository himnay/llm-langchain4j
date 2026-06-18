package com.org.llm.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String conversationId,
        @NotBlank(message = "message is required")
        String message,
        /** When set, scopes RAG retrieval to the document whose {@code fileName} metadata matches this value. */
        String documentSource
) {
}
