package com.familyhub.demo.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateChoreRequest(
        @NotNull
        Boolean completed
) {
}
