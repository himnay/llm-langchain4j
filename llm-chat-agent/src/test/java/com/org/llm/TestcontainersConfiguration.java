package com.org.llm;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18");
    }

    // No Redis container here: LLMApplicationTests mocks the EmbeddingStore bean instead, since a
    // real one (RedisEmbeddingStore) calls FT.CREATE on construction, which needs the RediSearch
    // module (redis/redis-stack-server), not the plain redis:7-alpine image used previously.
}
