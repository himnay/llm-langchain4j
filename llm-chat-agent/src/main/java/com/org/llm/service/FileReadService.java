package com.org.llm.service;

import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class FileReadService {

    private final ChatModel chatModel;

    public String readFile(String fileName, String message) {
        String base64 = readBase64("files/" + fileName);
        UserMessage userMessage = UserMessage.from(
                TextContent.from(message),
                PdfFileContent.from(base64, "application/pdf"));
        return chatModel.chat(userMessage).aiMessage().text();
    }

    private static String readBase64(String classpathLocation) {
        try {
            byte[] bytes = new ClassPathResource(classpathLocation).getInputStream().readAllBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + classpathLocation, e);
        }
    }
}
