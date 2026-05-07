package com.familyhub.demo.dto;

import com.familyhub.demo.model.ListKind;

import java.util.UUID;

public record ListCategoryResponse(
        UUID id,
        ListKind kind,
        String name,
        boolean seeded,
        int sortOrder
) {
}
