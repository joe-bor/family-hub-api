package com.familyhub.demo.dto;

import com.familyhub.demo.model.FamilyColor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record FamilyMemberRequest(
    @NotEmpty
    @Size(max = 30, message = "Name must be 30 characters or less")
    String name,

    @NotNull
    FamilyColor color,

    @Email
    String email,

    String avatarUrl
) {
}
