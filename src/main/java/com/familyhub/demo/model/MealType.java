package com.familyhub.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum MealType {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner");

    private final String wireValue;

    MealType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static MealType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Meal type is required.");
        }
        return Arrays.stream(values())
                .filter(type -> type.wireValue.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown meal type: " + value));
    }
}
