package com.familyhub.demo.dto;

public record AuthResponse(
        String token,
        FamilyResponseDto data
) {
}
