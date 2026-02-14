package com.familyhub.demo.mapper;

import com.familyhub.demo.dto.FamilyResponse;
import com.familyhub.demo.model.Family;

public class FamilyMapper {
    private FamilyMapper() {}

    public static FamilyResponse toDto(Family family) {
        return new FamilyResponse(
                family.getId(),
                family.getName(),
                family.getFamilyMembers().stream()
                        .map(FamilyMemberMapper::toDto)
                        .toList(),
                family.getCreatedAt()
        );
    }
}
