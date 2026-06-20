package com.org.llm.config;

import com.org.llm.assistant.ChatAssistant;
import com.org.llm.assistant.TravelPlanAssistant;
import com.org.llm.guardrail.BlockedPhraseGuardrail;
import com.org.llm.observability.LoggingChatModelListener;
import com.org.llm.tool.ContactsTool;
import com.org.llm.tool.WeatherTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * LangChain4j replacement for the old Spring AI {@code ChatClient} + advisor chain: a
 * conversational {@code ChatModel}/{@code StreamingChatModel} pair, JDBC-backed chat memory, a
 * moderation model, and the {@code ChatAssistant}/{@code TravelPlanAssistant} {@code AiServices}
 * built from them (tools, RAG retrieval augmentor and the input guardrail are wired in here too,
 * since LangChain4j fixes them at {@code AiServices} build-time rather than per-call).
 */
@Configuration
class AIConfig {

    @Bean
    ChatModel chatModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey,
                        @Value("${app.chat.model:gpt-4o-mini}") String modelName,
                        LoggingChatModelListener loggingChatModelListener) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .listeners(List.of(loggingChatModelListener))
                .build();
    }

    @Bean
    StreamingChatModel streamingChatModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey,
                                          @Value("${app.chat.model:gpt-4o-mini}") String modelName,
                                          LoggingChatModelListener loggingChatModelListener) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .listeners(List.of(loggingChatModelListener))
                .build();
    }

    @Bean
    ModerationModel moderationModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey) {
        return OpenAiModerationModel.builder()
                .apiKey(apiKey)
                .build();
    }

    @Bean
    ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        // 50-message window persisted via JdbcChatMemoryStore — same shape as Spring AI's
        // MessageWindowChatMemory + JdbcChatMemoryRepository.
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(50)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    @Bean
    ChatAssistant chatAssistant(ChatModel chatModel,
                                StreamingChatModel streamingChatModel,
                                ChatMemoryProvider chatMemoryProvider,
                                ModerationModel moderationModel,
                                RetrievalAugmentor retrievalAugmentor,
                                BlockedPhraseGuardrail blockedPhraseGuardrail,
                                WeatherTools weatherTools,
                                ContactsTool contactsTool) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .moderationModel(moderationModel)
                .retrievalAugmentor(retrievalAugmentor)
                .inputGuardrails(blockedPhraseGuardrail)
                .tools(weatherTools, contactsTool)
                .build();
    }

    @Bean
    TravelPlanAssistant travelPlanAssistant(ChatModel chatModel) {
        return AiServices.builder(TravelPlanAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
