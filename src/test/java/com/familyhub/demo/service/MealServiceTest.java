package com.familyhub.demo.service;

import com.familyhub.demo.dto.MealBoardResponse;
import com.familyhub.demo.dto.MealEntryRequest;
import com.familyhub.demo.dto.MealSlotEntryResponse;
import com.familyhub.demo.dto.MealSlotResponse;
import com.familyhub.demo.dto.MoveMealSlotRequest;
import com.familyhub.demo.dto.UpsertMealSlotRequest;
import com.familyhub.demo.dto.DuplicateMealSlotRequest;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.model.MealCollisionMode;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.MealEntrySourceType;
import com.familyhub.demo.model.MealSlot;
import com.familyhub.demo.model.MealSlotEntry;
import com.familyhub.demo.model.MealSlotRole;
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

    @Test
    void upsertSlot_existingPrimaryRequiresCollisionMode() {
        MealSlot existing = mealSlot(0, MealType.DINNER, "Pizza Night", List.of(), null);
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                0,
                MealType.DINNER
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> mealService.upsertSlot(new UpsertMealSlotRequest(
                WEEK_START,
                0,
                MealType.DINNER,
                quickMeal("Tacos"),
                List.of(),
                null,
                null
        ), family)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Collision mode is required");
    }

    @Test
    void upsertSlot_replacePrimaryReplacesExistingPrimaryAndExtras() {
        MealSlot existing = mealSlot(0, MealType.DINNER, "Pizza Night", List.of("Garlic Knots"), "Old note");
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                0,
                MealType.DINNER
        )).thenReturn(Optional.of(existing));
        when(mealSlotRepository.saveAndFlush(existing)).thenAnswer(invocation -> savedSlot(invocation.getArgument(0)));

        MealSlotResponse response = mealService.upsertSlot(new UpsertMealSlotRequest(
                WEEK_START,
                0,
                MealType.DINNER,
                quickMeal("Tacos"),
                List.of(quickMeal("Guacamole")),
                "New note",
                MealCollisionMode.REPLACE_PRIMARY
        ), family);

        assertThat(response.primary().title()).isEqualTo("Tacos");
        assertThat(response.extras()).extracting(MealSlotEntryResponse::title).containsExactly("Guacamole");
        assertThat(response.note()).isEqualTo("New note");
    }

    @Test
    void upsertSlot_addAsExtraPreservesExistingPrimaryAndAppendsRequestedBlock() {
        MealSlot existing = mealSlot(0, MealType.DINNER, "Pizza Night", List.of("Garlic Knots"), "Keep note");
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                0,
                MealType.DINNER
        )).thenReturn(Optional.of(existing));
        when(mealSlotRepository.saveAndFlush(existing)).thenAnswer(invocation -> savedSlot(invocation.getArgument(0)));

        MealSlotResponse response = mealService.upsertSlot(new UpsertMealSlotRequest(
                WEEK_START,
                0,
                MealType.DINNER,
                quickMeal("Tacos"),
                List.of(quickMeal("Guacamole")),
                "Ignored note",
                MealCollisionMode.ADD_AS_EXTRA
        ), family);

        assertThat(response.primary().title()).isEqualTo("Pizza Night");
        assertThat(response.extras()).extracting(MealSlotEntryResponse::title)
                .containsExactly("Garlic Knots", "Tacos", "Guacamole");
        assertThat(response.note()).isEqualTo("Keep note");
    }

    @Test
    void moveSlot_replacePrimaryMovesFullBlockAndClearsSource() {
        MealSlot source = mealSlot(1, MealType.DINNER, "Source Primary", List.of("Source Side"), "Source note");
        MealSlot destination = mealSlot(2, MealType.DINNER, "Destination Primary", List.of(), "Destination note");
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                1,
                MealType.DINNER
        )).thenReturn(Optional.of(source));
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                2,
                MealType.DINNER
        )).thenReturn(Optional.of(destination));
        when(mealSlotRepository.saveAndFlush(any(MealSlot.class))).thenAnswer(invocation -> savedSlot(invocation.getArgument(0)));
        when(mealSlotRepository.findByFamilyAndWeekStartDateOrderByDayIndexAscMealTypeAsc(family, WEEK_START))
                .thenReturn(List.of(source, destination));

        MealBoardResponse board = mealService.moveSlot(new MoveMealSlotRequest(
                WEEK_START,
                1,
                MealType.DINNER,
                WEEK_START,
                2,
                MealType.DINNER,
                MealCollisionMode.REPLACE_PRIMARY
        ), family);

        assertThat(board.days().get(1).slots().get(2).primary()).isNull();
        assertThat(board.days().get(2).slots().get(2).primary().title()).isEqualTo("Source Primary");
        assertThat(board.days().get(2).slots().get(2).extras()).extracting(MealSlotEntryResponse::title)
                .containsExactly("Source Side");
        assertThat(board.days().get(2).slots().get(2).note()).isEqualTo("Source note");
    }

    @Test
    void moveSlot_addAsExtraFlattensMovedUnitIntoDestinationExtrasAndClearsSource() {
        MealSlot source = mealSlot(1, MealType.DINNER, "Source Primary", List.of("Source Side"), null);
        MealSlot destination = mealSlot(2, MealType.DINNER, "Destination Primary", List.of("Destination Side"), null);
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                1,
                MealType.DINNER
        )).thenReturn(Optional.of(source));
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                2,
                MealType.DINNER
        )).thenReturn(Optional.of(destination));
        when(mealSlotRepository.saveAndFlush(any(MealSlot.class))).thenAnswer(invocation -> savedSlot(invocation.getArgument(0)));
        when(mealSlotRepository.findByFamilyAndWeekStartDateOrderByDayIndexAscMealTypeAsc(family, WEEK_START))
                .thenReturn(List.of(source, destination));

        MealBoardResponse board = mealService.moveSlot(new MoveMealSlotRequest(
                WEEK_START,
                1,
                MealType.DINNER,
                WEEK_START,
                2,
                MealType.DINNER,
                MealCollisionMode.ADD_AS_EXTRA
        ), family);

        assertThat(board.days().get(1).slots().get(2).primary()).isNull();
        assertThat(board.days().get(2).slots().get(2).primary().title()).isEqualTo("Destination Primary");
        assertThat(board.days().get(2).slots().get(2).extras()).extracting(MealSlotEntryResponse::title)
                .containsExactly("Destination Side", "Source Primary", "Source Side");
    }

    @Test
    void duplicateSlot_copiesFullBlockAndKeepsSourceSlot() {
        MealSlot source = mealSlot(1, MealType.DINNER, "Source Primary", List.of("Source Side"), "Source note");
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                1,
                MealType.DINNER
        )).thenReturn(Optional.of(source));
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                family,
                WEEK_START,
                4,
                MealType.DINNER
        )).thenReturn(Optional.empty());
        when(mealSlotRepository.saveAndFlush(any(MealSlot.class))).thenAnswer(invocation -> savedSlot(invocation.getArgument(0)));
        when(mealSlotRepository.findByFamilyAndWeekStartDateOrderByDayIndexAscMealTypeAsc(family, WEEK_START))
                .thenAnswer(invocation -> List.of(source, mealSlot(4, MealType.DINNER, "Source Primary", List.of("Source Side"), "Source note")));

        MealBoardResponse board = mealService.duplicateSlot(new DuplicateMealSlotRequest(
                WEEK_START,
                1,
                MealType.DINNER,
                WEEK_START,
                4,
                MealType.DINNER,
                MealCollisionMode.REPLACE_PRIMARY
        ), family);

        assertThat(board.days().get(1).slots().get(2).primary().title()).isEqualTo("Source Primary");
        assertThat(board.days().get(4).slots().get(2).primary().title()).isEqualTo("Source Primary");
        assertThat(board.days().get(4).slots().get(2).extras()).extracting(MealSlotEntryResponse::title)
                .containsExactly("Source Side");
        assertThat(board.days().get(4).slots().get(2).note()).isEqualTo("Source note");
    }

    @Test
    void moveSlot_usesFamilyScopedSourceLookup() {
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                otherFamily,
                WEEK_START,
                1,
                MealType.DINNER
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealService.moveSlot(new MoveMealSlotRequest(
                WEEK_START,
                1,
                MealType.DINNER,
                WEEK_START,
                2,
                MealType.DINNER,
                MealCollisionMode.REPLACE_PRIMARY
        ), otherFamily)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateSlot_usesFamilyScopedSourceLookup() {
        when(mealSlotRepository.findByFamilyAndWeekStartDateAndDayIndexAndMealType(
                otherFamily,
                WEEK_START,
                1,
                MealType.DINNER
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealService.duplicateSlot(new DuplicateMealSlotRequest(
                WEEK_START,
                1,
                MealType.DINNER,
                WEEK_START,
                2,
                MealType.DINNER,
                MealCollisionMode.REPLACE_PRIMARY
        ), otherFamily)).isInstanceOf(ResourceNotFoundException.class);
    }

    private MealEntryRequest quickMeal(String title) {
        return new MealEntryRequest(MealEntrySourceType.QUICK, null, title, null, null);
    }

    private MealEntryRequest recipeMeal(UUID recipeId) {
        return new MealEntryRequest(MealEntrySourceType.RECIPE, recipeId, null, null, null);
    }

    private MealSlot mealSlot(int dayIndex, MealType mealType, String primaryTitle, List<String> extras, String note) {
        MealSlot slot = new MealSlot();
        slot.setId(UUID.randomUUID());
        slot.setFamily(family);
        slot.setWeekStartDate(WEEK_START);
        slot.setDayIndex(dayIndex);
        slot.setMealType(mealType);
        slot.setNote(note);
        slot.getEntries().add(entry(slot, MealSlotRole.PRIMARY, 0, primaryTitle));
        for (int i = 0; i < extras.size(); i++) {
            slot.getEntries().add(entry(slot, MealSlotRole.EXTRA, i + 1, extras.get(i)));
        }
        return slot;
    }

    private MealSlotEntry entry(MealSlot slot, MealSlotRole role, int sortOrder, String title) {
        MealSlotEntry entry = new MealSlotEntry();
        entry.setId(UUID.randomUUID());
        entry.setSlot(slot);
        entry.setRole(role);
        entry.setSortOrder(sortOrder);
        entry.setSourceType(MealEntrySourceType.QUICK);
        entry.setTitleSnapshot(title);
        return entry;
    }

    private MealSlot savedSlot(MealSlot slot) {
        slot.setId(UUID.randomUUID());
        for (MealSlotEntry entry : slot.getEntries()) {
            entry.setId(UUID.randomUUID());
        }
        return slot;
    }
}
