package com.familyhub.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CalendarEventResponse(
        UUID id,
        String title,
        String startTime,
        String endTime,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,
        UUID memberId,
        boolean isAllDay,
        String location
) {

}
