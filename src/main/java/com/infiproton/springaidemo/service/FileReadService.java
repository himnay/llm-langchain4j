package com.infiproton.springaidemo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
@RequiredArgsConstructor
public class FileReadService {

    private final ChatClient chatClient;

    public String readFile(String fileName, String message) {
        Resource fileResource = new ClassPathResource("files/" + fileName);
        return chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(message)
                        .media(MediaType.APPLICATION_PDF, fileResource))
                .call()
                .content();
    }

}
