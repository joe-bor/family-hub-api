package com.familyhub.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CalendarEventRequest(
        @NotEmpty
        @Size(max = 100, message = "Event title must be 100 characters or less")
        String title,

        @NotEmpty
        @Pattern(regexp = "^(1[0-2]|[1-9]):[0-5][0-9] (AM|PM)$", message = "Time must be in 12-hour format (e.g., 2:00 PM)")
        String startTime,

        @NotEmpty
        @Pattern(regexp = "^(1[0-2]|[1-9]):[0-5][0-9] (AM|PM)$", message = "Time must be in 12-hour format (e.g., 2:00 PM)")
        String endTime,

        @NotEmpty
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])$")
        String date,

        @NotNull
        UUID memberId,

        // optional
        Boolean isAllDay,

        // optional
        @Size(max = 255)
        String location
) {

}