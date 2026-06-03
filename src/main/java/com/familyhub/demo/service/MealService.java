package com.familyhub.demo.service;

import com.familyhub.demo.dto.MealBoardResponse;
import com.familyhub.demo.dto.MealDayResponse;
import com.familyhub.demo.dto.MealEntryRequest;
import com.familyhub.demo.dto.MealSlotResponse;
import com.familyhub.demo.dto.UpsertMealSlotRequest;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.mapper.MealMapper;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.MealEntrySourceType;
import com.familyhub.demo.model.MealSlot;
import com.familyhub.demo.model.MealSlotEntry;
import com.familyhub.demo.model.MealSlotRole;
import com.familyhub.demo.model.MealType;
import com.familyhub.demo.model.RecipeConstraints;
import com.familyhub.demo.model.Recipe;
import com.familyhub.demo.repository.MealSlotRepository;
import com.familyhub.demo.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealService {
    private final MealSlotRepository mealSlotRepository;
    private final RecipeRepository recipeRepository;

    public MealBoardResponse getBoard(LocalDate weekStartDate, Family family) {
        validateWeekStartDate(weekStartDate);
        List<MealSlot> slots = mealSlotRepository.findByFamilyAndWeekStartDateOrderByDayIndexAscMealTypeAsc(
                family,
                weekStartDate
        );
        Map<SlotKey, MealSlot> slotsByKey = slots.stream()
                .collect(Collectors.toMap(slot -> new SlotKey(slot.getDayIndex(), slot.getMealType()), Function.identity()));

        List<MealDayResponse> days = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            int currentDayIndex = dayIndex;
            List<MealSlotResponse> slotResponses = List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
                    .stream()
                    .map(mealType -> {
                        MealSlot slot = slotsByKey.get(new SlotKey(currentDayIndex, mealType));
                        return slot == null
                                ? MealMapper.emptySlot(weekStartDate, currentDayIndex, mealType)
                                : MealMapper.toSlotDto(slot);
                    })
                    .toList();
            days.add(new MealDayResponse(weekStartDate.plusDays(dayIndex), dayIndex, slotResponses));
        }

        return new MealBoardResponse(weekStartDate, days);
    }

    @Transactional
    public MealSlotResponse upsertSlot(UpsertMealSlotRequest request, Family family) {
        validateWeekStartDate(request.weekStartDate());
        MealSlot slot = mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                request.weekStartDate(),
                request.dayIndex(),
                request.mealType()
        ).orElseGet(() -> newSlot(request, family));

        slot.setNote(RecipeFieldValidator.optionalText(request.note()));
        replaceEntries(slot, request.primary(), normalizedExtras(request.extras()));

        return MealMapper.toSlotDto(mealSlotRepository.saveAndFlush(slot));
    }

    private MealSlot newSlot(UpsertMealSlotRequest request, Family family) {
        MealSlot slot = new MealSlot();
        slot.setFamily(family);
        slot.setWeekStartDate(request.weekStartDate());
        slot.setDayIndex(request.dayIndex());
        slot.setMealType(request.mealType());
        return slot;
    }

    private void replaceEntries(MealSlot slot, MealEntryRequest primary, List<MealEntryRequest> extras) {
        slot.getEntries().clear();
        slot.getEntries().add(snapshotEntry(primary, slot, MealSlotRole.PRIMARY, 0));
        for (int i = 0; i < extras.size(); i++) {
            slot.getEntries().add(snapshotEntry(extras.get(i), slot, MealSlotRole.EXTRA, i + 1));
        }
    }

    private List<MealEntryRequest> normalizedExtras(List<MealEntryRequest> extras) {
        if (extras == null) {
            return List.of();
        }
        return extras;
    }

    private MealSlotEntry snapshotEntry(MealEntryRequest request, MealSlot slot, MealSlotRole role, int sortOrder) {
        MealSlotEntry entry = new MealSlotEntry();
        entry.setSlot(slot);
        entry.setRole(role);
        entry.setSortOrder(sortOrder);
        entry.setSourceType(request.sourceType());

        if (request.sourceType() == MealEntrySourceType.RECIPE) {
            if (request.recipeId() == null) {
                throw new BadRequestException("Recipe id is required for recipe-backed meals.");
            }
            Recipe recipe = recipeRepository.findByIdAndFamily(request.recipeId(), slot.getFamily())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe", request.recipeId()));
            entry.setRecipe(recipe);
            entry.setTitleSnapshot(recipe.getTitle());
            entry.setImageUrlSnapshot(recipe.getImageUrl());
            entry.setNoteSnapshot(recipeNoteSnapshot(request, recipe));
        } else {
            entry.setTitleSnapshot(quickMealTitle(request.title()));
            entry.setImageUrlSnapshot(RecipeFieldValidator.optionalHttpUrl(request.imageUrl(), "Meal image URL"));
            entry.setNoteSnapshot(RecipeFieldValidator.optionalText(request.note()));
        }

        return entry;
    }

    private String recipeNoteSnapshot(MealEntryRequest request, Recipe recipe) {
        String mealSpecificNote = RecipeFieldValidator.optionalText(request.note());
        return mealSpecificNote == null ? recipe.getNote() : mealSpecificNote;
    }

    private String quickMealTitle(String value) {
        String title = RecipeFieldValidator.optionalText(value);
        if (title == null) {
            throw new BadRequestException("Meal title is required.");
        }
        if (title.length() > RecipeConstraints.TITLE_MAX_LENGTH) {
            throw new BadRequestException("Meal title must be 160 characters or less.");
        }
        return title;
    }

    private void validateWeekStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new BadRequestException("Week start date is required.");
        }
        if (weekStartDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
            throw new BadRequestException("Week start date must be a Sunday.");
        }
    }

    private record SlotKey(int dayIndex, MealType mealType) {
    }
}
