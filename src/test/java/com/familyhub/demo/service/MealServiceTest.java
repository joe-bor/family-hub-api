package com.familyhub.demo.service;

import com.familyhub.demo.dto.MealBoardResponse;
import com.familyhub.demo.dto.MealEntryRequest;
import com.familyhub.demo.dto.MealSlotEntryResponse;
import com.familyhub.demo.dto.MealSlotResponse;
import com.familyhub.demo.dto.UpsertMealSlotRequest;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.MealEntrySourceType;
import com.familyhub.demo.model.MealSlot;
import com.familyhub.demo.model.MealSlotEntry;
import com.familyhub.demo.model.MealType;
import com.familyhub.demo.model.Recipe;
import com.familyhub.demo.repository.MealSlotRepository;
import com.familyhub.demo.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.familyhub.demo.TestDataFactory.createFamily;
import static com.familyhub.demo.TestDataFactory.createOtherFamily;
import static com.familyhub.demo.TestDataFactory.createRecipe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 6, 7);

    @Mock
    private MealSlotRepository mealSlotRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @InjectMocks
    private MealService mealService;

    private Family family;
    private Family otherFamily;

    @BeforeEach
    void setUp() {
        family = createFamily();
        otherFamily = createOtherFamily();
    }

    @Test
    void getBoard_returnsSevenDaysWithBreakfastLunchDinnerSlots() {
        when(mealSlotRepository.findByFamilyAndWeekStartDateOrderByDayIndexAscMealTypeAsc(family, WEEK_START))
                .thenReturn(List.of());

        MealBoardResponse board = mealService.getBoard(WEEK_START, family);

        assertThat(board.weekStartDate()).isEqualTo(WEEK_START);
        assertThat(board.days()).hasSize(7);
        assertThat(board.days()).extracting(day -> day.date())
                .containsExactly(
                        WEEK_START,
                        WEEK_START.plusDays(1),
                        WEEK_START.plusDays(2),
                        WEEK_START.plusDays(3),
                        WEEK_START.plusDays(4),
                        WEEK_START.plusDays(5),
                        WEEK_START.plusDays(6)
                );
        assertThat(board.days().getFirst().slots()).extracting(MealSlotResponse::mealType)
                .containsExactly(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER);
        assertThat(board.days().getFirst().slots()).allSatisfy(slot -> {
            assertThat(slot.primary()).isNull();
            assertThat(slot.extras()).isEmpty();
        });
    }

    @Test
    void upsertSlot_createsQuickMealPrimaryAndExtras() {
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                2,
                MealType.DINNER
        )).thenReturn(Optional.empty());
        when(mealSlotRepository.saveAndFlush(any(MealSlot.class))).thenAnswer(invocation -> savedSlot(invocation.getArgument(0)));

        MealSlotResponse response = mealService.upsertSlot(new UpsertMealSlotRequest(
                WEEK_START,
                2,
                MealType.DINNER,
                quickMeal("Leftovers"),
                List.of(quickMeal("Salad"), quickMeal("Bread")),
                "Bring the good plates",
                null
        ), family);

        assertThat(response.weekStartDate()).isEqualTo(WEEK_START);
        assertThat(response.dayIndex()).isEqualTo(2);
        assertThat(response.mealType()).isEqualTo(MealType.DINNER);
        assertThat(response.note()).isEqualTo("Bring the good plates");
        assertThat(response.primary().sourceType()).isEqualTo(MealEntrySourceType.QUICK);
        assertThat(response.primary().title()).isEqualTo("Leftovers");
        assertThat(response.extras()).extracting(MealSlotEntryResponse::title)
                .containsExactly("Salad", "Bread");
    }

    @Test
    void upsertSlot_resolvesRecipeThroughFamilyAndStoresBoardSnapshot() {
        Recipe recipe = createRecipe(family, "Original Tacos");
        recipe.setImageUrl("https://cdn.example.com/tacos.jpg");
        recipe.setNote("Use the small tortillas");
        AtomicReference<MealSlot> saved = new AtomicReference<>();

        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                1,
                MealType.DINNER
        )).thenReturn(Optional.empty());
        when(recipeRepository.findByIdAndFamily(recipe.getId(), family)).thenReturn(Optional.of(recipe));
        when(mealSlotRepository.saveAndFlush(any(MealSlot.class))).thenAnswer(invocation -> {
            MealSlot slot = savedSlot(invocation.getArgument(0));
            saved.set(slot);
            return slot;
        });

        mealService.upsertSlot(new UpsertMealSlotRequest(
                WEEK_START,
                1,
                MealType.DINNER,
                recipeMeal(recipe.getId()),
                List.of(),
                null,
                null
        ), family);
        recipe.setTitle("Updated Tacos");
        recipe.setImageUrl("https://cdn.example.com/updated.jpg");
        recipe.setNote("Updated recipe note");
        when(mealSlotRepository.findByFamilyAndWeekStartDateOrderByDayIndexAscMealTypeAsc(family, WEEK_START))
                .thenReturn(List.of(saved.get()));

        MealBoardResponse board = mealService.getBoard(WEEK_START, family);

        MealSlotEntryResponse plannedMeal = board.days().get(1).slots().get(2).primary();
        assertThat(plannedMeal.sourceType()).isEqualTo(MealEntrySourceType.RECIPE);
        assertThat(plannedMeal.recipeId()).isEqualTo(recipe.getId());
        assertThat(plannedMeal.title()).isEqualTo("Original Tacos");
        assertThat(plannedMeal.imageUrl()).isEqualTo("https://cdn.example.com/tacos.jpg");
        assertThat(plannedMeal.note()).isEqualTo("Use the small tortillas");
        verify(recipeRepository).findByIdAndFamily(recipe.getId(), family);
    }

    @Test
    void upsertSlot_doesNotPlaceRecipeFromAnotherFamily() {
        Recipe otherRecipe = createRecipe(otherFamily, "Other Soup");
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                0,
                MealType.LUNCH
        )).thenReturn(Optional.empty());
        when(recipeRepository.findByIdAndFamily(otherRecipe.getId(), family)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealService.upsertSlot(new UpsertMealSlotRequest(
                WEEK_START,
                0,
                MealType.LUNCH,
                recipeMeal(otherRecipe.getId()),
                List.of(),
                null,
                null
        ), family)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upsertSlot_persistsTheAuthenticatedFamilyOnNewSlots() {
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                3,
                MealType.BREAKFAST
        )).thenReturn(Optional.empty());
        when(mealSlotRepository.saveAndFlush(any(MealSlot.class))).thenAnswer(invocation -> savedSlot(invocation.getArgument(0)));

        mealService.upsertSlot(new UpsertMealSlotRequest(
                WEEK_START,
                3,
                MealType.BREAKFAST,
                quickMeal("Bagels"),
                List.of(),
                null,
                null
        ), family);

        ArgumentCaptor<MealSlot> captor = ArgumentCaptor.forClass(MealSlot.class);
        verify(mealSlotRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getFamily()).isEqualTo(family);
    }

    private MealEntryRequest quickMeal(String title) {
        return new MealEntryRequest(MealEntrySourceType.QUICK, null, title, null, null);
    }

    private MealEntryRequest recipeMeal(UUID recipeId) {
        return new MealEntryRequest(MealEntrySourceType.RECIPE, recipeId, null, null, null);
    }

    private MealSlot savedSlot(MealSlot slot) {
        slot.setId(UUID.randomUUID());
        for (MealSlotEntry entry : slot.getEntries()) {
            entry.setId(UUID.randomUUID());
        }
        return slot;
    }
}
