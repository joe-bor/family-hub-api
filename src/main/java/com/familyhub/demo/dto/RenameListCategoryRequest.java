package com.familyhub.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameListCategoryRequest(
        @NotBlank @Size(max = 100) String name
) {}
