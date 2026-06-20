package com.org.llm.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Postgres-backed {@link ChatMemoryStore}: one row per conversation holding its full message
 * window serialized as JSON (replaces Spring AI's {@code JdbcChatMemoryRepository}/
 * {@code spring-ai-starter-model-chat-memory-repository-jdbc}; LangChain4j has no JDBC store of
 * its own, only this pluggable interface — see {@code db/migration/V6__create_chat_memory.sql}).
 */
@Component
@RequiredArgsConstructor
public class JdbcChatMemoryStore implements ChatMemoryStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return Optional.ofNullable(jdbcTemplate.query(
                        "SELECT messages FROM chat_memory WHERE conversation_id = ?",
                        (rs, rowNum) -> rs.getString("messages"),
                        memoryId.toString()))
                .filter(rows -> !rows.isEmpty())
                .map(rows -> ChatMessageDeserializer.messagesFromJson(rows.get(0)))
                .orElseGet(List::of);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        int updated = jdbcTemplate.update(
                "UPDATE chat_memory SET messages = ?, updated_at = NOW() WHERE conversation_id = ?",
                json, memoryId.toString());
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO chat_memory (conversation_id, messages) VALUES (?, ?)",
                    memoryId.toString(), json);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        jdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", memoryId.toString());
    }
}
