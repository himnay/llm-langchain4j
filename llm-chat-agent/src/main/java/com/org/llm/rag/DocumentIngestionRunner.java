package com.org.llm.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Indexes the two corporate documents (travel policy + events/holidays PDFs) into the Redis
 * embedding store on startup, using LangChain4j's document-parser/splitter/
 * {@link EmbeddingStoreIngestor} pipeline. Nothing in this module did this before — ingestion
 * lived in a separate upstream service — but the {@code app.documents.*} config already pointed
 * at these two files, so this exercises the part of LangChain4j's API the rest of the module
 * (retrieval only) never touches.
 */
@Slf4j
@Component
public class DocumentIngestionRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final String travelPolicyPath;
    private final String eventsPath;

    public DocumentIngestionRunner(JdbcTemplate jdbcTemplate,
                                    EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> embeddingStore,
                                    @Value("${app.documents.travel-policy.file.path}") String travelPolicyPath,
                                    @Value("${app.documents.events.file.path}") String eventsPath) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.travelPolicyPath = travelPolicyPath;
        this.eventsPath = eventsPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        ingestIfNeeded(travelPolicyPath);
        ingestIfNeeded(eventsPath);
    }

    private void ingestIfNeeded(String classpathLocation) {
        String fileName = classpathLocation.substring(classpathLocation.lastIndexOf('/') + 1);
        if (alreadyIngested(fileName)) {
            log.debug("Skipping ingestion, already indexed: {}", fileName);
            return;
        }
        try {
            Document document = parse(classpathLocation, fileName);
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 100))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(document);
            markIngested(fileName);
            log.info("Ingested document into Redis embedding store: {}", fileName);
        } catch (Exception e) {
            log.warn("Failed to ingest document {}: {}", fileName, e.getMessage());
        }
    }

    private Document parse(String classpathLocation, String fileName) throws java.io.IOException {
        DocumentParser parser = new ApachePdfBoxDocumentParser();
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            Document document = parser.parse(in);
            document.metadata()
                    .put("fileName", fileName)
                    .put("source", classpathLocation)
                    .put("identity", fileName);
            return document;
        }
    }

    private boolean alreadyIngested(String fileName) {
        return !jdbcTemplate.queryForList(
                "SELECT 1 FROM ingested_documents WHERE file_name = ?", fileName).isEmpty();
    }

    private void markIngested(String fileName) {
        jdbcTemplate.update("INSERT INTO ingested_documents (file_name) VALUES (?)", fileName);
    }
}
