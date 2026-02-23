package com.familyhub.demo.mapper;

import com.familyhub.demo.dto.CalendarEventRequest;
import com.familyhub.demo.dto.CalendarEventResponse;
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
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);



    public static CalendarEvent toEntity(CalendarEventRequest request, Family family, FamilyMember familyMember) {
        CalendarEvent calendarEvent = new CalendarEvent();

        calendarEvent.setTitle(request.title());
        calendarEvent.setStartTime(stringToLocalTime(request.startTime()));
        calendarEvent.setEndTime(stringToLocalTime(request.endTime()));
        calendarEvent.setDate(stringToDate(request.date()));
        calendarEvent.setMember(familyMember);
        calendarEvent.setFamily(family);
        calendarEvent.setAllDay(request.isAllDay() != null && request.isAllDay());
        calendarEvent.setLocation(request.location());

        return  calendarEvent;
    }

    public static CalendarEventResponse toDto(CalendarEvent calendarEvent) {
        return CalendarEventResponse.builder()
                .id(calendarEvent.getId())
                .title(calendarEvent.getTitle())
                .startTime(timeToString(calendarEvent.getStartTime()))
                .endTime(timeToString(calendarEvent.getEndTime()))
                .date(dateToString(calendarEvent.getDate()))
                .memberId(calendarEvent.getMember().getId())
                .isAllDay(calendarEvent.isAllDay())
                .location(calendarEvent.getLocation())
                .build();
    }

    private static String timeToString(LocalTime localTime) {
        return localTime.format(timeFormatter);
    }

    private static LocalTime stringToLocalTime(String time) {
        try {
            return LocalTime.parse(time, timeFormatter);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Error Parsing: " + time);
        }
    }

    private static String dateToString(LocalDate localDate) {
        return localDate.format(dateFormatter);
    }

    private static LocalDate stringToDate(String date) {
        try {
            return LocalDate.parse(date, dateFormatter);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Error Parsing: " + date);
        }
    }
}
