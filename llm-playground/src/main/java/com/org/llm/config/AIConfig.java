package com.org.llm.config;

import com.org.llm.assistant.ClassifierAssistant;
import com.org.llm.assistant.ExtractionAssistant;
import com.org.llm.assistant.SummarizerAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    ModerationModel moderationModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey) {
        return OpenAiModerationModel.builder()
                .apiKey(apiKey)
                .build();
    }

    @Bean
    ClassifierAssistant classifierAssistant(ChatModel chatModel) {
        return AiServices.create(ClassifierAssistant.class, chatModel);
    }

    @Bean
    ExtractionAssistant extractionAssistant(ChatModel chatModel) {
        return AiServices.create(ExtractionAssistant.class, chatModel);
    }

    @Bean
    SummarizerAssistant summarizerAssistant(ChatModel chatModel) {
        return AiServices.create(SummarizerAssistant.class, chatModel);
    }
}
