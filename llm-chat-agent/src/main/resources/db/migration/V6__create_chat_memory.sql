-- Backs JdbcChatMemoryStore: one row per conversation, holding its full message window as JSON
-- (LangChain4j's ChatMessageSerializer/Deserializer round-trip the List<ChatMessage>).
CREATE TABLE IF NOT EXISTS chat_memory (
    conversation_id VARCHAR(100) PRIMARY KEY,
    messages        TEXT        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
