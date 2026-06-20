package com.org.llm.model;

import jakarta.validation.constraints.NotBlank;

public record TextRequest(@NotBlank(message = "text is required") String text) {
}
