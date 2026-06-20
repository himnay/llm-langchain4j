package com.org.llm.assistant;

import com.org.llm.model.TravelPlan;
import dev.langchain4j.service.UserMessage;

/**
 * Structured-output {@code AiServices} contract: returning {@link TravelPlan} directly makes
 * LangChain4j derive a JSON schema from the record and parse the model's reply into it — the
 * same role Spring AI's {@code ChatClient.call().entity(TravelPlan.class)} played.
 */
public interface TravelPlanAssistant {

    TravelPlan plan(@UserMessage String prompt);
}
