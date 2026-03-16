package com.familyhub.demo.dto;

import java.util.List;

public record CalendarSelectionRequest(
        List<String> calendarIds
) {}
