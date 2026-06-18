package com.org.llm.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response body from {@code llm-chat-agent}'s {@code POST /api/v1/chat} (subset of its
 * {@code ChatAnswer} — voice chat only needs the answer text, not citations/faithfulness).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatAgentChatResponse(String answer) {
}
