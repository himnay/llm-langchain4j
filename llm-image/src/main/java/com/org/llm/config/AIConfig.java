package com.org.llm.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal {@code ChatModel} for {@code ImageCaptionService}'s multimodal (image + text) calls —
 * single-shot, no memory/guardrails needed. {@code ImageModel} backs {@code LocalImageBackend}'s
 * generation (OpenAI Dall-E — LangChain4j has no Stability AI integration at all, see README).
 */
@Configuration
class AIConfig {

    @Bean
    ChatModel chatModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey,
                       @Value("${app.chat.model:gpt-4o-mini}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @Bean
    ImageModel imageModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey,
                         @Value("${app.image.model:dall-e-3}") String modelName) {
        return OpenAiImageModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
