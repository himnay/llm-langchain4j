package com.org.llm.rag;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Folds conversation history + the current message into one standalone query
 * ({@link CompressingQueryTransformer}), then paraphrases that into several variants
 * ({@link ExpandingQueryTransformer}) to improve vector-store recall on ambiguous questions.
 *
 * <p>{@code DefaultRetrievalAugmentor} only has a single {@code queryTransformer} slot (unlike
 * Spring AI's {@code RetrievalAugmentationAdvisor}, which takes a transformer list plus a
 * separate expander) — composing the two LangChain4j transformers in plain Java here reproduces
 * the same compress-then-expand pipeline as {@code RagConfig}'s old advisor.</p>
 */
@RequiredArgsConstructor
public class CompressThenExpandQueryTransformer implements QueryTransformer {

    private final CompressingQueryTransformer compressingQueryTransformer;
    private final ExpandingQueryTransformer expandingQueryTransformer;

    @Override
    public Collection<Query> transform(Query query) {
        Collection<Query> compressed = compressingQueryTransformer.transform(query);
        Collection<Query> expanded = new LinkedHashSet<>();
        for (Query compressedQuery : compressed) {
            expanded.addAll(expandingQueryTransformer.transform(compressedQuery));
        }
        return expanded;
    }
}
