package com.org.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for routing image generation through {@code llm-gateway} (prefix {@code app.gateway}).
 *
 * <p>When {@link #enabled} is true, image generation is sent to the gateway
 * ({@code POST /llm/image}) instead of calling Stability AI directly, so the gateway owns
 * provider keys, guardrails and failover.</p>
 */
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayProperties {

    /** Route image traffic through the gateway. */
    private boolean enabled = true;

    /** Gateway base URL including its {@code /llm} base path. */
    private String baseUrl = "http://localhost:8080/llm";

    /** Raw API key for the gateway's {@code X-API-Key} header (only needed when the gateway has auth on). */
    private String apiKey = "";

    /** Image model used by {@code POST /llm/image}. */
    private String imageModel = "dall-e-3";

    /** Per-call timeout (seconds) for blocking gateway requests. */
    private int timeoutSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getImageModel() { return imageModel; }
    public void setImageModel(String imageModel) { this.imageModel = imageModel; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
