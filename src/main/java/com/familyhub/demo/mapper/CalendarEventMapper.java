package com.familyhub.demo.mapper;

import com.familyhub.demo.dto.CalendarEventRequest;
import com.familyhub.demo.dto.CalendarEventResponse;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class CalendarEventMapper {
    private CalendarEventMapper() {}
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US);



    public static CalendarEvent toEntity(CalendarEventRequest request, Family family, FamilyMember familyMember) {
        CalendarEvent calendarEvent = new CalendarEvent();

        calendarEvent.setTitle(request.title());
        calendarEvent.setStartTime(parseTime(request.startTime()));
        calendarEvent.setEndTime(parseTime(request.endTime()));
        calendarEvent.setDate(request.date());
        calendarEvent.setMember(familyMember);
        calendarEvent.setFamily(family);
        calendarEvent.setAllDay(request.isAllDay() != null && request.isAllDay());
        calendarEvent.setLocation(request.location());
        calendarEvent.setEndDate(request.endDate());
        calendarEvent.setRecurrenceRule(request.recurrenceRule());

        return  calendarEvent;
    }

    public static CalendarEventResponse toDto(CalendarEvent calendarEvent) {
        return CalendarEventResponse.builder()
                .id(calendarEvent.getId())
                .title(calendarEvent.getTitle())
                .startTime(timeToString(calendarEvent.getStartTime()))
                .endTime(timeToString(calendarEvent.getEndTime()))
                .date(calendarEvent.getDate())
                .memberId(calendarEvent.getMember().getId())
                .isAllDay(calendarEvent.isAllDay())
                .location(calendarEvent.getLocation())
                .endDate(calendarEvent.getEndDate())
                .recurrenceRule(calendarEvent.getRecurrenceRule())
                .recurringEventId(calendarEvent.getRecurringEvent() != null
                        ? calendarEvent.getRecurringEvent().getId() : null)
                .isRecurring(calendarEvent.getRecurrenceRule() != null
                        || calendarEvent.getRecurringEvent() != null)
                .build();
    }

    public static CalendarEventResponse toInstanceResponse(CalendarEvent parent, LocalDate instanceDate) {
        return CalendarEventResponse.builder()
                .id(null)
                .title(parent.getTitle())
                .startTime(timeToString(parent.getStartTime()))
                .endTime(timeToString(parent.getEndTime()))
                .date(instanceDate)
                .memberId(parent.getMember().getId())
                .isAllDay(parent.isAllDay())
                .location(parent.getLocation())
                .endDate(null)
                .recurrenceRule(null)
                .recurringEventId(parent.getId())
                .isRecurring(true)
                .build();
    }

    private static String timeToString(LocalTime localTime) {
        return localTime.format(timeFormatter);
    }

    public static LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time, timeFormatter);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Error Parsing: " + time);
        }
    }
}
