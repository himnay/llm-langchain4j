package com.org.llm.rag;

import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Component;

/**
 * Carries the current request's vector-store filter so the singleton
 * {@code EmbeddingStoreContentRetriever} bean (see {@code RagConfig}) can apply a per-request
 * {@link Filter} via its {@code dynamicFilter(Function<Query, Filter>)} hook without being
 * rebuilt on every call. {@code LocalChatBackend} sets the value before invoking the
 * {@code ChatAssistant} and clears it in a {@code finally} block.
 */
@Component
public class RagFilterContext {

    private final ThreadLocal<Filter> filter = new ThreadLocal<>();

    public void set(Filter value) {
        filter.set(value);
    }

    public Filter get() {
        return filter.get();
    }

    public void clear() {
        filter.remove();
    }
}
