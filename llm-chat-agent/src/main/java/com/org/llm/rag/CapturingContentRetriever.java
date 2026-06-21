package com.org.llm.rag;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Decorator that records every retrieval's results into {@link RetrievedContentContext}.
 */
@RequiredArgsConstructor
public class CapturingContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;
    private final RetrievedContentContext context;

    @Override
    public List<Content> retrieve(Query query) {
        List<Content> retrieved = delegate.retrieve(query);
        context.set(retrieved);
        return retrieved;
    }
}
