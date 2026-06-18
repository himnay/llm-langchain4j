package com.org.llm.model;

import jakarta.validation.constraints.NotBlank;

public record ImageCaptionRequest(
        @NotBlank(message = "imageName is required")
        String imageName,
        @NotBlank(message = "message is required")
        String message
) {
}
