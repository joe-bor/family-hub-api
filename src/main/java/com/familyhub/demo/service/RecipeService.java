package com.familyhub.demo.service;

import com.familyhub.demo.dto.CreateRecipeRequest;
import com.familyhub.demo.dto.ImportRecipeRequest;
import com.familyhub.demo.dto.RecipeDetailResponse;
import com.familyhub.demo.dto.RecipeSummaryResponse;
import com.familyhub.demo.dto.UpdateRecipeRequest;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.mapper.RecipeMapper;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.Recipe;
import com.familyhub.demo.model.RecipeIngredient;
import com.familyhub.demo.model.RecipeInstruction;
import com.familyhub.demo.model.RecipeTag;
import com.familyhub.demo.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final RecipeImportService recipeImportService;

    public List<RecipeSummaryResponse> getRecipes(Family family) {
        return recipeRepository.findByFamilyForSummary(family)
                .stream()
                .map(RecipeMapper::toSummaryDto)
                .toList();
    }

    public RecipeDetailResponse getRecipe(UUID id, Family family) {
        return RecipeMapper.toDetailDto(getRecipeOrThrow(id, family));
    }

    @Transactional
    public RecipeDetailResponse createRecipe(CreateRecipeRequest request, Family family) {
        Recipe recipe = new Recipe();
        recipe.setFamily(family);
        recipe.setTitle(requiredTitle(request.title()));
        recipe.setImageUrl(optionalText(request.imageUrl()));
        recipe.setNote(optionalText(request.note()));
        recipe.setSourceUrl(optionalText(request.sourceUrl()));
        recipe.setFavorite(Boolean.TRUE.equals(request.favorite()));
        replaceIngredients(recipe, request.ingredients());
        replaceInstructions(recipe, request.instructions());
        replaceTags(recipe, request.tags());

        return RecipeMapper.toDetailDto(recipeRepository.saveAndFlush(recipe));
    }

    @Transactional
    public RecipeDetailResponse updateRecipe(UUID id, UpdateRecipeRequest request, Family family) {
        Recipe recipe = getRecipeOrThrow(id, family);
        if (request.hasTitle()) {
            recipe.setTitle(requiredTitle(request.title()));
        }
        if (request.hasImageUrl()) {
            recipe.setImageUrl(optionalText(request.imageUrl()));
        }
        if (request.hasIngredients()) {
            replaceIngredients(recipe, request.ingredients());
        }
        if (request.hasInstructions()) {
            replaceInstructions(recipe, request.instructions());
        }
        if (request.hasNote()) {
            recipe.setNote(optionalText(request.note()));
        }
        if (request.hasSourceUrl()) {
            recipe.setSourceUrl(optionalText(request.sourceUrl()));
        }
        if (request.hasTags()) {
            replaceTags(recipe, request.tags());
        }
        if (request.hasFavorite()) {
            recipe.setFavorite(Boolean.TRUE.equals(request.favorite()));
        }
        recipe.setUpdatedAt(LocalDateTime.now());

        return RecipeMapper.toDetailDto(recipeRepository.saveAndFlush(recipe));
    }

    @Transactional
    public RecipeDetailResponse importRecipe(ImportRecipeRequest request, Family family) {
        ImportedRecipe imported = recipeImportService.importFromUrl(request.url().trim());
        Recipe recipe = new Recipe();
        recipe.setFamily(family);
        recipe.setTitle(requiredTitle(imported.title()));
        recipe.setImageUrl(optionalText(imported.imageUrl()));
        recipe.setNote(optionalText(imported.note()));
        recipe.setSourceUrl(optionalText(imported.sourceUrl()));
        recipe.setFavorite(imported.favorite());
        replaceIngredients(recipe, imported.ingredients());
        replaceInstructions(recipe, imported.instructions());
        replaceTags(recipe, imported.tags());

        return RecipeMapper.toDetailDto(recipeRepository.saveAndFlush(recipe));
    }

    private Recipe getRecipeOrThrow(UUID id, Family family) {
        return recipeRepository.findByIdAndFamily(id, family)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe", id));
    }

    private void replaceIngredients(Recipe recipe, List<String> values) {
        recipe.getIngredients().clear();
        List<String> normalized = normalizedList(values);
        for (int i = 0; i < normalized.size(); i++) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            ingredient.setSortOrder(i);
            ingredient.setText(normalized.get(i));
            recipe.getIngredients().add(ingredient);
        }
    }

    private void replaceInstructions(Recipe recipe, List<String> values) {
        recipe.getInstructions().clear();
        List<String> normalized = normalizedList(values);
        for (int i = 0; i < normalized.size(); i++) {
            RecipeInstruction instruction = new RecipeInstruction();
            instruction.setRecipe(recipe);
            instruction.setSortOrder(i);
            instruction.setText(normalized.get(i));
            recipe.getInstructions().add(instruction);
        }
    }

    private void replaceTags(Recipe recipe, List<String> values) {
        recipe.getTags().clear();
        List<String> normalized = new LinkedHashSet<>(normalizedList(values)).stream().toList();
        for (int i = 0; i < normalized.size(); i++) {
            RecipeTag tag = new RecipeTag();
            tag.setRecipe(recipe);
            tag.setSortOrder(i);
            tag.setName(normalized.get(i));
            recipe.getTags().add(tag);
        }
    }

    private List<String> normalizedList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::optionalText)
                .filter(value -> value != null)
                .toList();
    }

    private String requiredTitle(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new BadRequestException("Recipe title is required.");
        }
        return normalized;
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
