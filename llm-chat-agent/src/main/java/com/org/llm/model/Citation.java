package com.org.llm.model;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.rag.content.Content;

/**
 * A single retrieved-document reference backing a RAG answer, surfaced to the client so it can
 * show "answered using X" provenance.
 *
 * <p>Fields mirror the metadata keys written by {@link com.org.llm.rag.DocumentIngestionRunner}
 * into the Redis embedding store ({@code fileName}, {@code source}, {@code identity}); the chunk
 * position comes from LangChain4j's own splitter-assigned {@code index} metadata key. {@code page}
 * is always {@code null} today — {@code ApachePdfBoxDocumentParser} extracts whole-document text,
 * not per-page, so there's no page number to carry (kept nullable rather than removed, in case a
 * page-aware parser replaces it later).</p>
 */
public record Citation(
        String fileName,
        String source,
        String identity,
        Integer chunkIndex,
        Integer page
) {

    public static Citation from(Content content) {
        Metadata metadata = content.textSegment().metadata();
        return new Citation(
                metadata.getString("fileName"),
                metadata.getString("source"),
                metadata.getString("identity"),
                metadata.getInteger("index"),
                metadata.getInteger("page_number")
        );
    }
}
