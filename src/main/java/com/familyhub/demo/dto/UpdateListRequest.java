package com.familyhub.demo.dto;

import com.familyhub.demo.model.ListCategoryDisplayMode;
import jakarta.validation.constraints.NotNull;

public record UpdateListRequest(
        @NotNull
        ListCategoryDisplayMode categoryDisplayMode,

        Boolean showCompletedOverride
) {
}
