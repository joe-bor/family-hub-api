package com.familyhub.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRecipeRequest(
        @NotBlank
        @Size(max = 160, message = "Recipe title must be 160 characters or less")
        String title,

        String imageUrl,

        List<@NotBlank @Size(max = 500) String> ingredients,

        List<@NotBlank String> instructions,

        String note,

        String sourceUrl,

        List<@NotBlank @Size(max = 60) String> tags,

        Boolean favorite
) {
}
