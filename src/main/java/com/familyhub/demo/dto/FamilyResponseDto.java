package com.familyhub.demo.dto;

import com.familyhub.demo.model.Family;

import java.time.LocalDateTime;
import java.util.UUID;

public record FamilyResponseDto (
        UUID id,
        String name,
        // TODO: add familyMembers
        LocalDateTime createdAt
) {
    public static FamilyResponseDto toDto(Family family) {
        return new FamilyResponseDto(
                family.getId(),
                family.getName(),
                family.getCreatedAt()
        );
    }
}
