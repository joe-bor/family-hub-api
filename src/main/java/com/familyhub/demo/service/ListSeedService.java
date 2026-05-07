package com.familyhub.demo.service;

import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.ListCategory;
import com.familyhub.demo.model.ListKind;
import com.familyhub.demo.model.ListPreferences;
import com.familyhub.demo.repository.ListCategoryRepository;
import com.familyhub.demo.repository.ListPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListSeedService {

    private static final Map<ListKind, List<String>> SEEDED_CATEGORY_NAMES = Map.of(
            ListKind.GROCERY, List.of("Produce", "Dairy", "Pantry", "Frozen", "Household"),
            ListKind.TODO, List.of("Urgent", "Soon", "Later")
    );

    private final ListPreferencesRepository listPreferencesRepository;
    private final ListCategoryRepository listCategoryRepository;

    @Transactional
    public void seedDefaultsForFamily(Family family) {
        ensurePreferencesExist(family);
        ensureSeededCategoriesExist(family);
    }

    private void ensurePreferencesExist(Family family) {
        if (listPreferencesRepository.findByFamily(family).isPresent()) {
            return;
        }

        ListPreferences preferences = new ListPreferences();
        preferences.setFamily(family);
        preferences.setShowCompletedByDefault(true);
        listPreferencesRepository.save(preferences);
    }

    private void ensureSeededCategoriesExist(Family family) {
        List<ListCategory> categoriesToSave = new ArrayList<>();

        SEEDED_CATEGORY_NAMES.forEach((kind, names) -> {
            Map<String, ListCategory> existingByName = listCategoryRepository
                    .findByFamilyAndKindOrderBySortOrderAsc(family, kind)
                    .stream()
                    .collect(Collectors.toMap(ListCategory::getName, Function.identity()));

            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index);
                ListCategory category = existingByName.getOrDefault(name, new ListCategory());
                category.setFamily(family);
                category.setKind(kind);
                category.setName(name);
                category.setSeeded(true);
                category.setSortOrder(index);
                categoriesToSave.add(category);
            }
        });

        if (!categoriesToSave.isEmpty()) {
            listCategoryRepository.saveAll(categoriesToSave);
        }
    }
}
