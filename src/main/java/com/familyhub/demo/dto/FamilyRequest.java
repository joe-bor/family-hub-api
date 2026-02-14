package com.familyhub.demo.dto;

import jakarta.validation.constraints.Size;

public record FamilyRequest(
        @Size(min = 1, message = "Name cannot be blank")
        String name,

        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        String username
) {}
