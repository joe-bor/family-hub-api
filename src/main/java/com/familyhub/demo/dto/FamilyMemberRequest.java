package com.familyhub.demo.dto;

import com.familyhub.demo.model.FamilyColor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;


public record FamilyMemberRequest(
    @NotEmpty
    String name,

    @NotNull
    FamilyColor color,

    @Email
    String email,

    String avatarUrl
) {}
