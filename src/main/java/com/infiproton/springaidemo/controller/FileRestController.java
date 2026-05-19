package com.infiproton.springaidemo.controller;

import com.infiproton.springaidemo.model.ChatRequest;
import com.infiproton.springaidemo.service.FileReadService;
import com.infiproton.springaidemo.service.ImageCaptionService;
import com.infiproton.springaidemo.service.ImageGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/file")
class FileRestController {

    private final FileReadService fileReadService;
    private final ImageGenerationService imageGenerationService;

    @PostMapping("/read")
    public String caption(@RequestBody ChatRequest chatRequest) {
        return fileReadService.readFile(chatRequest.imageName(), chatRequest.message());
    }
}
