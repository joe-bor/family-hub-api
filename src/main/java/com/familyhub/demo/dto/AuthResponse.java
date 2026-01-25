package com.familyhub.demo.dto;

import com.familyhub.demo.model.Family;

public record AuthResponse(String token, Family family) {
}
