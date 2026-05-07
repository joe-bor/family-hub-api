package com.familyhub.demo.service;

import com.familyhub.demo.dto.ClearCompletedResponse;
import com.familyhub.demo.dto.CreateListItemRequest;
import com.familyhub.demo.dto.UpdateListItemRequest;
import com.familyhub.demo.dto.UpdateListRequest;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.ListCategory;
import com.familyhub.demo.model.ListCategoryDisplayMode;
import com.familyhub.demo.model.ListKind;
import com.familyhub.demo.model.SharedList;
import com.familyhub.demo.repository.ListCategoryRepository;
import com.familyhub.demo.repository.ListPreferencesRepository;
import com.familyhub.demo.repository.SharedListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.LIST_ID;
import static com.familyhub.demo.TestDataFactory.LIST_ITEM_ID;
import static com.familyhub.demo.TestDataFactory.createFamily;
import static com.familyhub.demo.TestDataFactory.createGeneralList;
import static com.familyhub.demo.TestDataFactory.createGroceryList;
import static com.familyhub.demo.TestDataFactory.createListCategory;
import static com.familyhub.demo.TestDataFactory.createListItem;
import static com.familyhub.demo.TestDataFactory.createListWithCompletedItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListServiceTest {

    @Mock
    private SharedListRepository sharedListRepository;

    @Mock
    private ListCategoryRepository listCategoryRepository;

    @Mock
    private ListPreferencesRepository listPreferencesRepository;

    @InjectMocks
    private ListService listService;

    private Family family;
    private SharedList groceryList;
    private ListCategory produceCategory;

    @BeforeEach
    void setUp() {
        family = createFamily();
        groceryList = createGroceryList(family);
        produceCategory = createListCategory(family, ListKind.GROCERY, "Produce", 0);
        groceryList.setItems(new ArrayList<>(List.of(
                createListItem(groceryList, LIST_ITEM_ID, "Bananas", produceCategory, false, null)
        )));
    }

    @Test
    void getLists_returnsFamilyScopedSummaries() {
        when(sharedListRepository.findByFamilyWithItems(family)).thenReturn(List.of(createListWithCompletedItems(family)));

        var result = listService.getLists(family);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().totalItems()).isEqualTo(3);
        assertThat(result.getFirst().completedItems()).isEqualTo(2);
    }

    @Test
    void updateList_generalListGroupedMode_throwsBadRequest() {
        SharedList generalList = createGeneralList(family);
        when(sharedListRepository.findDetailByFamilyAndId(family, LIST_ID)).thenReturn(Optional.of(generalList));

        assertThatThrownBy(() -> listService.updateList(
                LIST_ID,
                new UpdateListRequest(ListCategoryDisplayMode.GROUPED, null),
                family
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createItem_generalListWithCategory_throwsBadRequest() {
        SharedList generalList = createGeneralList(family);
        when(sharedListRepository.findDetailByFamilyAndId(family, LIST_ID)).thenReturn(Optional.of(generalList));

        assertThatThrownBy(() -> listService.createItem(
                LIST_ID,
                new CreateListItemRequest("Paper towels", produceCategory.getId()),
                family
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateItem_categoryFromWrongKind_throwsBadRequest() {
        ListCategory todoCategory = createListCategory(family, ListKind.TODO, "Urgent", 0);
        when(sharedListRepository.findDetailByFamilyAndId(family, LIST_ID)).thenReturn(Optional.of(groceryList));
        when(listCategoryRepository.findByFamilyAndId(family, todoCategory.getId())).thenReturn(Optional.of(todoCategory));

        assertThatThrownBy(() -> listService.updateItem(
                LIST_ID,
                LIST_ITEM_ID,
                new UpdateListItemRequest("Bananas", false, todoCategory.getId()),
                family
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateItem_missingItem_throwsResourceNotFound() {
        when(sharedListRepository.findDetailByFamilyAndId(family, LIST_ID)).thenReturn(Optional.of(groceryList));

        assertThatThrownBy(() -> listService.updateItem(
                LIST_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                new UpdateListItemRequest("Bananas", false, null),
                family
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearCompleted_removesCompletedItemsAndReturnsCount() {
        when(sharedListRepository.findDetailByFamilyAndId(family, LIST_ID))
                .thenReturn(Optional.of(createListWithCompletedItems(family)));
        when(sharedListRepository.saveAndFlush(any(SharedList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClearCompletedResponse response = listService.clearCompleted(LIST_ID, family);

        assertThat(response.removedCount()).isEqualTo(2);
        verify(sharedListRepository).saveAndFlush(any(SharedList.class));
    }
}
