package com.familyhub.demo.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CalendarEventResponse(
        UUID id,
        String title,
        String startTime,
        String endTime,
        String date,
        UUID memberId,
        boolean isAllDay,
        String location
) {

}
