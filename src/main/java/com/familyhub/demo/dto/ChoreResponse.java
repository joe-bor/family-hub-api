package com.familyhub.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChoreResponse(
        UUID id,
        String title,
        UUID assignedToMemberId,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dueDate,
        boolean completed,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
