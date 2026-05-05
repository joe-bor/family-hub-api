package com.familyhub.demo.mapper;

import com.familyhub.demo.dto.ChoreResponse;
import com.familyhub.demo.model.Chore;

public class ChoreMapper {
    private ChoreMapper() {
    }

    public static ChoreResponse toDto(Chore chore) {
        return new ChoreResponse(
                chore.getId(),
                chore.getTitle(),
                chore.getAssignedToMember().getId(),
                chore.getDueDate(),
                chore.isCompleted(),
                chore.getCompletedAt(),
                chore.getCreatedAt(),
                chore.getUpdatedAt()
        );
    }
}
