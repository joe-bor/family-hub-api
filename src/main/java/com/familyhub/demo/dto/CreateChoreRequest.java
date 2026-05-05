package com.familyhub.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateChoreRequest(
        @NotBlank
        @Size(max = 100, message = "Chore title must be 100 characters or less")
        String title,

        @NotNull
        UUID assignedToMemberId,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dueDate
) {
}
