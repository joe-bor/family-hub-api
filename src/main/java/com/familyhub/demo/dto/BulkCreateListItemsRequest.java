package com.familyhub.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateListItemsRequest(
        @Valid
        @NotEmpty(message = "At least one item is required")
        @Size(max = 100, message = "A bulk append may contain at most 100 items")
        List<CreateListItemRequest> items
) {
}
