package com.infiproton.springaidemo.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final PDFLoader pdfLoader;
    private final VectorStore vectorStore;

    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    @Value("${app.documents.travel-policy.file.path}")
    private String travelPolicyFilePath;

    @Value("${app.documents.events.file.path}")
    private String eventsFilePath;

    public void initialize() throws IOException {
        String travelPolicyText = pdfLoader.loadPDF(travelPolicyFilePath);
        String eventsText = pdfLoader.loadPDF(eventsFilePath);

        vectorStore.add(textSplitter.split(new Document(travelPolicyText)));
        vectorStore.add(textSplitter.split(new Document(eventsText)));

        log.info("✅ Vector store initialized with both PDF file's content.");
    }

}
