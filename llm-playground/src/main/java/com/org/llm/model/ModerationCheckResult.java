package com.org.llm.model;

/** Result of a standalone {@code ModerationModel} call (see {@code ModerationService}). */
public record ModerationCheckResult(boolean flagged, String flaggedText) {
}
