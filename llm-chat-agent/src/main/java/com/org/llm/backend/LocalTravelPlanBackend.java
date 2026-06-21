package com.org.llm.backend;

import com.org.llm.assistant.TravelPlanAssistant;
import com.org.llm.model.TravelPlan;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Uses {@link TravelPlanAssistant}'s structured-output support to map straight to {@link TravelPlan}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.gateway", name = "enabled", havingValue = "false")
public class LocalTravelPlanBackend implements TravelPlanBackend {

    private final TravelPlanAssistant travelPlanAssistant;

    @Override
    public TravelPlan plan(PromptTemplate template, Map<String, Object> variables) {
        String prompt = template.apply(variables).text();
        return travelPlanAssistant.plan(prompt);
    }
}
