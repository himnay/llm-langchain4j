package com.org.llm.client.dto;

/** Request body for {@code llm-chat-agent}'s {@code POST /api/v1/chat} (mirrors its {@code ChatRequest}). */
public record ChatAgentChatRequest(String conversationId, String message, String documentSource) {
}
