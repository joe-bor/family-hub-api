package com.familyhub.demo.controller;

import com.familyhub.demo.dto.ApiResponse;
import com.familyhub.demo.dto.CalendarEventRequest;
import com.familyhub.demo.dto.CalendarEventResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.service.CalendarEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/events")
@RequiredArgsConstructor
public class CalendarEventController {
    private final CalendarEventService calendarEventService;


    @GetMapping
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getAllCalendarEventsByFamily(@AuthenticationPrincipal Family family) {
        List<CalendarEventResponse> allEventsByFamily = calendarEventService.getAllEventsByFamily(family);
        return ResponseEntity.ok(new ApiResponse<>(allEventsByFamily, ""));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> getCalendarEventById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Family family
    ) {
        CalendarEventResponse event = calendarEventService.getEventById(id, family);

        return ResponseEntity.ok(new ApiResponse<>(event, ""));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> addCalendarEvent(
            @Valid @RequestBody CalendarEventRequest request,
            @AuthenticationPrincipal Family family
            ) {
        CalendarEventResponse calendarEventResponse = calendarEventService.addCalendarEvent(request, family);

        URI location = URI.create("/api/calendar/events/" + calendarEventResponse.id());
        return ResponseEntity.created(location).body(new ApiResponse<>(calendarEventResponse, "Event created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> updateCalendarEvent(
            @Valid @RequestBody CalendarEventRequest request,
            @PathVariable UUID id,
            @AuthenticationPrincipal Family family
    ) {
        CalendarEventResponse response = calendarEventService.updateCalendarEvent(request, id, family);

        return ResponseEntity.ok(new ApiResponse<>(response, "Event updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCalendarEvent(
            @PathVariable UUID id,
            @AuthenticationPrincipal Family family
    ) {
        calendarEventService.deleteCalendarEvent(id, family);

        return ResponseEntity.noContent().build();
    }
}
