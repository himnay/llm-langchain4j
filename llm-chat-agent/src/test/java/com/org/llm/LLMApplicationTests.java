package com.org.llm;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "app.weather.api-key=test-key",
        "OPENAI_API_KEY=test-key"
})
@Import(TestcontainersConfiguration.class)
class LLMApplicationTests {

    @MockitoBean
    private EmbeddingStore<TextSegment> embeddingStore;

    @DisplayName("Spring application context loads successfully")
    @Test
    void contextLoads() {
    }
}
