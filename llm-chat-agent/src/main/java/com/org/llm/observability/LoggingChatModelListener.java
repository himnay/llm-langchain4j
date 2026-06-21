package com.org.llm.observability;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logs every chat-model request/response/error — replaces Spring AI's {@code SimpleLoggerAdvisor}.
 */
@Slf4j
@Component
public class LoggingChatModelListener implements ChatModelListener {

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        log.debug("LLM request [{}]: {}", requestContext.modelProvider(), requestContext.chatRequest().messages());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        log.debug("LLM response [{}]: {}", responseContext.modelProvider(), responseContext.chatResponse().aiMessage());
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("LLM call failed [{}]: {}", errorContext.modelProvider(), errorContext.error().getMessage());
    }
}
