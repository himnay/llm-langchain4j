package com.org.llm.config;

import com.org.llm.assistant.FaithfulnessJudge;
import com.org.llm.rag.CapturingContentRetriever;
import com.org.llm.rag.CompressThenExpandQueryTransformer;
import com.org.llm.rag.RagFilterContext;
import com.org.llm.rag.RetrievedContentContext;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import java.util.Set;

/**
 * Beans for LangChain4j's modular RAG pipeline: the pre-retrieval query transformer, the Redis
 * {@link EmbeddingStore}/{@link EmbeddingModel}, and the {@link RetrievalAugmentor} wired onto
 * {@code ChatAssistant} in {@code AIConfig} — same retrieve → filter → augment → generate shape
 * Spring AI's {@code RetrievalAugmentationAdvisor} had, built from LangChain4j's primitives.
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
class RagConfig {

    @Bean
    JedisPooled jedisPooled(@Value("${spring.data.redis.host:localhost}") String host,
                             @Value("${spring.data.redis.port:6379}") int port,
                             @Value("${spring.data.redis.database:0}") int database,
                             @Value("${spring.data.redis.username:}") String username,
                             @Value("${spring.data.redis.password:}") String password) {
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .database(database)
                .user(username.isBlank() ? null : username)
                .password(password.isBlank() ? null : password)
                .build();
        return new JedisPooled(new HostAndPort(host, port), clientConfig);
    }

    @Bean
    EmbeddingModel embeddingModel(@Value("${app.embedding.model:text-embedding-3-small}") String modelName,
                                   @Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(JedisPooled jedisPooled,
                                                @Value("${app.embedding.dimension:1536}") int dimension,
                                                @Value("${app.rag.redis.index-name:travel-documents}") String indexName,
                                                @Value("${app.rag.redis.prefix:travel-documents:}") String prefix) {
        return RedisEmbeddingStore.builder()
                .unifiedJedis(jedisPooled)
                .indexName(indexName)
                .prefix(prefix)
                .dimension(dimension)
                .metadataKeys(Set.of("fileName", "source", "identity", "chunkIndex", "page_number"))
                .build();
    }

    @Bean
    @Qualifier("ragChatModel")
    ChatModel ragChatModel(@Value("${OPENAI_API_KEY:sk-placeholder}") String apiKey,
                           @Value("${app.chat.model:gpt-4o-mini}") String modelName) {
        // Low temperature keeps query transformation/expansion deterministic; kept separate from
        // the main conversational ChatModel (AIConfig) so it never affects answer generation.
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.0)
                .build();
    }

    @Bean
    FaithfulnessJudge faithfulnessJudge(@Qualifier("ragChatModel") ChatModel ragChatModel) {
        return AiServices.create(FaithfulnessJudge.class, ragChatModel);
    }

    @Bean
    CompressingQueryTransformer compressingQueryTransformer(@Qualifier("ragChatModel") ChatModel ragChatModel) {
        return CompressingQueryTransformer.builder().chatModel(ragChatModel).build();
    }

    @Bean
    ExpandingQueryTransformer expandingQueryTransformer(@Qualifier("ragChatModel") ChatModel ragChatModel) {
        return ExpandingQueryTransformer.builder().chatModel(ragChatModel).n(3).build();
    }

    @Bean
    QueryTransformer queryTransformer(CompressingQueryTransformer compressingQueryTransformer,
                                       ExpandingQueryTransformer expandingQueryTransformer) {
        return new CompressThenExpandQueryTransformer(compressingQueryTransformer, expandingQueryTransformer);
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                       EmbeddingModel embeddingModel,
                                       RagFilterContext ragFilterContext,
                                       RetrievedContentContext retrievedContentContext) {
        // dynamicFilter ignores the Query argument and reads the ThreadLocal LocalChatBackend sets
        // before each call — the same per-request document-scoping trick Spring AI's
        // VectorStoreDocumentRetriever.filterExpression(ragFilterContext::get) used.
        EmbeddingStoreContentRetriever delegate = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .dynamicFilter(query -> ragFilterContext.get())
                .build();
        return new CapturingContentRetriever(delegate, retrievedContentContext);
    }

    @Bean
    ContentAggregator contentAggregator() {
        return new DefaultContentAggregator();
    }

    @Bean
    ContentInjector contentInjector() {
        return DefaultContentInjector.builder().build();
    }

    @Bean
    RetrievalAugmentor retrievalAugmentor(QueryTransformer queryTransformer,
                                           ContentRetriever contentRetriever,
                                           ContentAggregator contentAggregator,
                                           ContentInjector contentInjector) {
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .contentAggregator(contentAggregator)
                .contentInjector(contentInjector)
                .build();
    }
}
