package com.org.llm.model;

import jakarta.validation.constraints.NotBlank;

public record FileReadRequest(
        @NotBlank(message = "fileName is required")
        String fileName,
        @NotBlank(message = "message is required")
        String message
) {
}
