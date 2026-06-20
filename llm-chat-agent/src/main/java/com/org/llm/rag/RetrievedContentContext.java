package com.org.llm.rag;

import dev.langchain4j.rag.content.Content;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carries the content retrieved for the current request out of the singleton RAG pipeline.
 * LangChain4j's plain {@code String}-returning {@code AiServices} methods (unlike Spring AI's
 * {@code ChatClientResponse}) expose no per-call hook into what was retrieved, so
 * {@link CapturingContentRetriever} stashes it here for {@code LocalChatBackend} to read right
 * after the call (streaming uses {@code TokenStream#onRetrieved} instead, which gets this for
 * free).
 */
@Component
public class RetrievedContentContext {

    private final ThreadLocal<List<Content>> retrieved = new ThreadLocal<>();

    public void set(List<Content> value) {
        retrieved.set(value);
    }

    public List<Content> get() {
        List<Content> value = retrieved.get();
        return value == null ? List.of() : value;
    }

    public void clear() {
        retrieved.remove();
    }
}
