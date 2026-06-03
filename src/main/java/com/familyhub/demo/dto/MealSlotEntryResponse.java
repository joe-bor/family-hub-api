package com.familyhub.demo.dto;

import com.familyhub.demo.model.MealEntrySourceType;

import java.util.UUID;

public record MealSlotEntryResponse(
        UUID id,
        MealEntrySourceType sourceType,
        UUID recipeId,
        String title,
        String imageUrl,
        String note
) {
}
