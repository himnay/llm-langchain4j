package com.org.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for calling {@code llm-chat-agent} to get the AI reply after transcribing voice input
 * (prefix {@code app.chat-agent}).
 */
@ConfigurationProperties(prefix = "app.chat-agent")
public class ChatAgentProperties {

    /** llm-chat-agent base URL including its API base path. */
    private String baseUrl = "http://localhost:8082/ai/api/v1";

    /** Raw API key for llm-chat-agent's {@code X-API-Key} header (only needed when its auth is on). */
    private String apiKey = "";

    /** Per-call timeout (seconds) for the blocking chat-agent request. */
    private int timeoutSeconds = 60;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
