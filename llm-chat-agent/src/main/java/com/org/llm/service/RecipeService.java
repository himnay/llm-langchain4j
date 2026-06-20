package com.org.llm.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final PromptTemplate DRAFT_TEMPLATE =
            PromptTemplate.from("Write a recipe for {{dish}}. Include ingredients and preparation steps.");
    private static final PromptTemplate REFINE_TEMPLATE =
            PromptTemplate.from("Here is the recipe:\n{{draft}}");

    private final ChatModel chatModel;

    public String getDraftRecipe(String dish) {
        String prompt = DRAFT_TEMPLATE.apply(Map.of("dish", dish)).text();
        return chatModel.chat(prompt);
    }

    public String refineRecipe(String draft) {
        SystemMessage systemMessage = SystemMessage.from(
                "You are a recipe formatter. Convert recipes into JSON with keys: 'dish', 'ingredients', 'steps'.");
        UserMessage userMessage = REFINE_TEMPLATE.apply(Map.of("draft", draft)).toUserMessage();
        return chatModel.chat(systemMessage, userMessage).aiMessage().text();
    }
}
