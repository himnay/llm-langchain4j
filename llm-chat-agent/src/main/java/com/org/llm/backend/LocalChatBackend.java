package com.org.llm.backend;

import com.org.llm.assistant.ChatAssistant;
import com.org.llm.config.RagProperties;
import com.org.llm.model.ChatAnswer;
import com.org.llm.model.Citation;
import com.org.llm.rag.RagFilterContext;
import com.org.llm.rag.RetrievedContentContext;
import com.org.llm.service.AnswerEvaluator;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Calls OpenAI directly via the local LangChain4j {@code ChatAssistant}, with JDBC-backed
 * conversation memory, the weather/contacts tools, and RAG over the corporate travel-policy /
 * events documents (via the {@code RetrievalAugmentor} wired onto it, see {@code RagConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.gateway", name = "enabled", havingValue = "false")
public class LocalChatBackend implements ChatBackend {

    private final ChatAssistant chatAssistant;
    private final AnswerEvaluator answerEvaluator;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final RagFilterContext ragFilterContext;
    private final RetrievedContentContext retrievedContentContext;

    @Override
    public ChatAnswer chat(String systemPrompt, String conversationId, String message, String documentSource) {
        ragFilterContext.set(documentFilter(documentSource));
        try {
            String answer = chatAssistant.chat(conversationId, systemPrompt, message);
            List<Content> documents = retrievedContentContext.get();
            List<Citation> citations = documents.stream().map(Citation::from).toList();
            Boolean faithful = evaluateFaithfulness(message, documents, answer, citations);
            return new ChatAnswer(answer, citations, faithful);
        } finally {
            ragFilterContext.clear();
            retrievedContentContext.clear();
        }
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(String conversationId, String message, String documentSource) {
        ragFilterContext.set(documentFilter(documentSource));
        // TokenStream#onRetrieved hands back exactly what this call retrieved, so citations no
        // longer need to be read back out of a shared context after the fact like Spring AI's
        // RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT did — they arrive as part of the stream.
        return Flux.<ServerSentEvent<String>>create(sink ->
                        chatAssistant.chatStream(conversationId, "", message)
                                .onPartialResponse(token -> sink.next(tokenEvent(token)))
                                .onRetrieved(retrieved -> sink.next(citationsEvent(toCitations(retrieved))))
                                .onCompleteResponse(response -> sink.complete())
                                .onError(sink::error)
                                .start())
                .doFinally(signal -> ragFilterContext.clear())
                .onErrorResume(throwable -> {
                    log.error("Error occurred in the stream", throwable);
                    return Flux.error(new IllegalStateException(
                            "Error occurred in the stream: %s".formatted(throwable.getMessage())));
                });
    }

    /** Scopes retrieval to one document (matched by its {@code fileName} metadata) when requested. */
    private static Filter documentFilter(String documentSource) {
        if (documentSource == null || documentSource.isBlank()) {
            return null;
        }
        return MetadataFilterBuilder.metadataKey("fileName").isEqualTo(documentSource);
    }

    private static List<Citation> toCitations(List<Content> retrieved) {
        return retrieved.stream().map(Citation::from).toList();
    }

    private Boolean evaluateFaithfulness(String question, List<Content> documents, String answer,
                                          List<Citation> citations) {
        if (!ragProperties.isEvaluateFaithfulness() || citations.isEmpty() || answer == null) {
            return null;
        }
        return answerEvaluator.isFaithful(question, documents, answer);
    }

    private ServerSentEvent<String> tokenEvent(String token) {
        return ServerSentEvent.<String>builder()
                .event("token")
                .data(token != null ? token : "")
                .build();
    }

    private ServerSentEvent<String> citationsEvent(List<Citation> citations) {
        return ServerSentEvent.<String>builder()
                .event("citations")
                .data(writeJson(citations))
                .build();
    }

    private String writeJson(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            log.warn("Failed to serialize citations: {}", e.getMessage());
            return "[]";
        }
    }
}
