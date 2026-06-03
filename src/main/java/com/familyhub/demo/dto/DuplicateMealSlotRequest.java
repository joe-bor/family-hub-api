package com.familyhub.demo.dto;

import com.familyhub.demo.model.MealCollisionMode;
import com.familyhub.demo.model.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DuplicateMealSlotRequest(
        @NotNull LocalDate sourceWeekStartDate,
        @Min(0) @Max(6) int sourceDayIndex,
        @NotNull MealType sourceMealType,
        @NotNull LocalDate destinationWeekStartDate,
        @Min(0) @Max(6) int destinationDayIndex,
        @NotNull MealType destinationMealType,
        @NotNull MealCollisionMode collisionMode
) {
}
