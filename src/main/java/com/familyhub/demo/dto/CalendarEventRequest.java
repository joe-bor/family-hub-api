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

    CalendarEventRequest(
            String title,
            String startTime,
            String endTime,
            String date,
            UUID memberId
    ) {
        this(title, startTime, endTime, date, memberId, false, "");
    }

    CalendarEventRequest(
            String title,
            String startTime,
            String endTime,
            String date,
            UUID memberId,
            boolean isAllDay
    ) {
        this(title, startTime, endTime, date, memberId, isAllDay, "");
    }

    CalendarEventRequest(
            String title,
            String startTime,
            String endTime,
            String date,
            UUID memberId,
            String location
    ) {
        this(title, startTime, endTime, date, memberId, false, location);
    }
}

/*
TODO: Create a compact constructor that enforces such shape.
The json below is the shape the FE sends

```json
{
    title: string;
    startTime: string;     // "2:00 PM" — 12-hour
    endTime: string;       // "3:00 PM" — 12-hour
    date: string;          // "yyyy-MM-dd"
    memberId: string;
    isAllDay?: boolean;
    location?: string;
}
```
*/