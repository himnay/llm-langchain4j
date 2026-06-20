package com.org.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Replaces Spring AI's auto-configured {@code OpenAiAudioTranscriptionModel}/
 * {@code OpenAiAudioSpeechModel} beans (no manual config existed before — the Spring Boot
 * starter wired them implicitly). LangChain4j covers transcription
 * ({@link AudioTranscriptionModel}) but has no text-to-speech abstraction, so the official
 * OpenAI Java SDK ({@link OpenAIClient}) is used directly for that in {@code AudioService}.
 */
@Configuration
class AIConfig {

    @Bean
    AudioTranscriptionModel audioTranscriptionModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey) {
        return OpenAiAudioTranscriptionModel.builder()
                .apiKey(apiKey)
                .modelName("whisper-1")
                .build();
    }

    @Bean
    OpenAIClient openAiClient(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey) {
        return OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }
}
