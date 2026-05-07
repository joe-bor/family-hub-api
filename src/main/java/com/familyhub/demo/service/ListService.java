package com.familyhub.demo.service;

import com.familyhub.demo.dto.*;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.mapper.ListMapper;
import com.familyhub.demo.model.*;
import com.familyhub.demo.repository.ListCategoryRepository;
import com.familyhub.demo.repository.ListPreferencesRepository;
import com.familyhub.demo.repository.SharedListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListService {
    private final SharedListRepository sharedListRepository;
    private final ListCategoryRepository listCategoryRepository;
    private final ListPreferencesRepository listPreferencesRepository;

    public List<ListSummaryResponse> getLists(Family family) {
        return sharedListRepository.findByFamilyWithItems(family)
                .stream()
                .map(ListMapper::toSummaryDto)
                .toList();
    }

    @Transactional
    public ListDetailResponse createList(CreateListRequest request, Family family) {
        SharedList list = new SharedList();
        list.setFamily(family);
        list.setName(request.name().trim());
        list.setKind(request.kind());
        list.setCategoryDisplayMode(request.kind() == ListKind.GENERAL
                ? ListCategoryDisplayMode.FLAT
                : ListCategoryDisplayMode.GROUPED);
        list.setShowCompletedOverride(null);

        return mapDetail(sharedListRepository.saveAndFlush(list));
    }

    public ListDetailResponse getList(UUID id, Family family) {
        return mapDetail(getListOrThrow(id, family));
    }

    @Transactional
    public ListDetailResponse updateList(UUID id, UpdateListRequest request, Family family) {
        SharedList list = getListOrThrow(id, family);
        if (list.getKind() == ListKind.GENERAL
                && request.categoryDisplayMode() == ListCategoryDisplayMode.GROUPED) {
            throw new BadRequestException("General lists cannot use grouped category mode.");
        }

        list.setCategoryDisplayMode(request.categoryDisplayMode());
        list.setShowCompletedOverride(request.showCompletedOverride());

        return mapDetail(sharedListRepository.saveAndFlush(list));
    }

    @Transactional
    public ListItemResponse createItem(UUID listId, CreateListItemRequest request, Family family) {
        SharedList list = getListOrThrow(listId, family);
        Set<UUID> existingItemIds = list.getItems().stream()
                .map(SharedListItem::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        ListCategory category = resolveCategory(list, request.categoryId());

        SharedListItem item = new SharedListItem();
        item.setList(list);
        item.setText(request.text().trim());
        item.setCompleted(false);
        item.setCompletedAt(null);
        item.setCategory(category);
        list.getItems().add(item);

        sharedListRepository.saveAndFlush(list);
        SharedList saved = getListOrThrow(listId, family);
        SharedListItem savedItem = saved.getItems().stream()
                .filter(candidate -> candidate.getId() != null && !existingItemIds.contains(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("List Item", listId));
        return ListMapper.toItemDto(savedItem);
    }

    @Transactional
    public ListItemResponse updateItem(UUID listId, UUID itemId, UpdateListItemRequest request, Family family) {
        SharedList list = getListOrThrow(listId, family);
        SharedListItem item = list.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("List Item", itemId));

        item.setText(request.text().trim());
        boolean completed = Boolean.TRUE.equals(request.completed());
        item.setCompleted(completed);
        if (completed) {
            if (item.getCompletedAt() == null) {
                item.setCompletedAt(LocalDateTime.now());
            }
        } else {
            item.setCompletedAt(null);
        }
        item.setCategory(resolveCategory(list, request.categoryId()));

        sharedListRepository.saveAndFlush(list);
        return ListMapper.toItemDto(item);
    }

    @Transactional
    public void deleteItem(UUID listId, UUID itemId, Family family) {
        SharedList list = getListOrThrow(listId, family);
        boolean removed = list.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResourceNotFoundException("List Item", itemId);
        }

        sharedListRepository.saveAndFlush(list);
    }

    @Transactional
    public ClearCompletedResponse clearCompleted(UUID listId, Family family) {
        SharedList list = getListOrThrow(listId, family);
        int before = list.getItems().size();
        list.getItems().removeIf(SharedListItem::isCompleted);
        sharedListRepository.saveAndFlush(list);
        return new ClearCompletedResponse(before - list.getItems().size());
    }

    public ListPreferencesResponse getPreferences(Family family) {
        ListPreferences preferences = listPreferencesRepository.findByFamily(family)
                .orElseThrow(() -> new ResourceNotFoundException("List Preferences", family.getId()));
        return new ListPreferencesResponse(preferences.isShowCompletedByDefault());
    }

    @Transactional
    public ListPreferencesResponse updatePreferences(UpdateListPreferencesRequest request, Family family) {
        ListPreferences preferences = listPreferencesRepository.findByFamily(family)
                .orElseThrow(() -> new ResourceNotFoundException("List Preferences", family.getId()));
        preferences.setShowCompletedByDefault(request.showCompletedByDefault());
        return new ListPreferencesResponse(listPreferencesRepository.saveAndFlush(preferences).isShowCompletedByDefault());
    }

    private ListCategory resolveCategory(SharedList list, UUID categoryId) {
        if (list.getKind() == ListKind.GENERAL) {
            if (categoryId != null) {
                throw new BadRequestException("General lists do not support categories.");
            }
            return null;
        }

        if (categoryId == null) {
            return null;
        }

        ListCategory category = listCategoryRepository.findByFamilyAndId(list.getFamily(), categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("List Category", categoryId));
        if (category.getKind() != list.getKind()) {
            throw new BadRequestException("Category kind does not match list kind.");
        }
        return category;
    }

    private SharedList getListOrThrow(UUID id, Family family) {
        return sharedListRepository.findDetailByFamilyAndId(family, id)
                .orElseThrow(() -> new ResourceNotFoundException("List", id));
    }

    private ListDetailResponse mapDetail(SharedList list) {
        List<ListCategoryResponse> categories = list.getKind() == ListKind.GENERAL
                ? List.of()
                : listCategoryRepository.findByFamilyAndKindOrderBySortOrderAsc(list.getFamily(), list.getKind())
                        .stream()
                        .map(ListMapper::toCategoryDto)
                        .toList();

        return ListMapper.toDetailDto(list, categories);
    }
}
