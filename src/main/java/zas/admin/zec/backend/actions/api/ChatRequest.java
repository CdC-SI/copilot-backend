package zas.admin.zec.backend.actions.api;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String input) {}
