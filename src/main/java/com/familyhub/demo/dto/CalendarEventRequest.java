package com.familyhub.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CalendarEventRequest(
        @NotEmpty
        String title,

        @NotEmpty
        String startTime,

        @NotEmpty
        String endTime,

        @NotEmpty
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])$")
        String date,

        @NotNull
        UUID memberId,

        // optional
        boolean isAllDay,

        // optional
        String location
) {

}