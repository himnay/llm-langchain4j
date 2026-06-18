package com.org.llm.config;

import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the {@link WebClient} used to call {@code llm-chat-agent} for the AI reply after
 * transcribing voice input.
 *
 * <p>The {@code X-Request-ID} from the current request's MDC context is propagated as
 * {@code X-Correlation-ID} so the two services' logs can be correlated for one voice-chat
 * exchange (same pattern as {@code GatewayConfig} in the sibling services).</p>
 */
@Configuration
@EnableConfigurationProperties(ChatAgentProperties.class)
public class ChatAgentConfig {

    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Bean
    WebClient chatAgentWebClient(ChatAgentProperties properties) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .filter(correlationIdFilter());

        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.defaultHeader("X-API-Key", properties.getApiKey());
        }
        return builder.build();
    }

    private static ExchangeFilterFunction correlationIdFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            String requestId = MDC.get(REQUEST_ID_MDC_KEY);
            if (requestId != null && !requestId.isBlank()) {
                ClientRequest mutated = ClientRequest.from(clientRequest)
                        .header(CORRELATION_ID_HEADER, requestId)
                        .build();
                return reactor.core.publisher.Mono.just(mutated);
            }
            return reactor.core.publisher.Mono.just(clientRequest);
        });
    }
}
