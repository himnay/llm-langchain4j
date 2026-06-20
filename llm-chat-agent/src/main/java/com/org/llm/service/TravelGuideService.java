package com.org.llm.service;

import com.org.llm.backend.TravelPlanBackend;
import com.org.llm.model.TravelPlan;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads the travel-guide prompt template and delegates structured-plan generation to the
 * active {@link TravelPlanBackend} strategy (gateway or local).
 */
@Service
public class TravelGuideService {

    private final TravelPlanBackend travelPlanBackend;
    private final PromptTemplate travelGuideTemplate;

    public TravelGuideService(TravelPlanBackend travelPlanBackend,
                              @Value("classpath:prompts/travel-guide.st") Resource travelGuideResource) {
        this.travelPlanBackend = travelPlanBackend;
        this.travelGuideTemplate = PromptTemplate.from(readResource(travelGuideResource));
    }

    public TravelPlan prepareTravelPlan(String city, Integer days) {
        return travelPlanBackend.plan(travelGuideTemplate, Map.of("city", city, "days", days));
    }

    private static String readResource(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read travel-guide prompt template", e);
        }
    }
}
