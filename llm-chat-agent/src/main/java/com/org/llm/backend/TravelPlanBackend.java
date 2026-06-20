package com.org.llm.backend;

import com.org.llm.model.TravelPlan;
import dev.langchain4j.model.input.PromptTemplate;

import java.util.Map;

/**
 * Strategy for producing a structured {@link TravelPlan} from the travel-guide prompt template:
 * via {@code llm-gateway} (free-form JSON parsed locally) or the local {@code TravelPlanAssistant}
 * (structured-output {@code AiServices}). Selected at startup by {@code app.gateway.enabled}.
 */
public interface TravelPlanBackend {

    TravelPlan plan(PromptTemplate template, Map<String, Object> variables);
}
