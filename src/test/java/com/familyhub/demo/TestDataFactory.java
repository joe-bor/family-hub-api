package com.familyhub.demo;

import com.familyhub.demo.dto.*;
import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyColor;
import com.familyhub.demo.model.FamilyMember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class TestDataFactory {

    public static final UUID FAMILY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID OTHER_FAMILY_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private TestDataFactory() {
    }

    // --- Entity factories ---

    public static Family createFamily() {
        Family family = new Family();
        family.setId(FAMILY_ID);
        family.setName("Test Family");
        family.setUsername("testfamily");
        family.setPasswordHash("$2a$10$dummyhashfortesting");
        family.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        family.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return family;
    }

    public static Family createOtherFamily() {
        Family family = new Family();
        family.setId(OTHER_FAMILY_ID);
        family.setName("Other Family");
        family.setUsername("otherfamily");
        family.setPasswordHash("$2a$10$dummyhashfortesting");
        family.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        family.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return family;
    }

    public static FamilyMember createFamilyMember(Family family) {
        FamilyMember member = new FamilyMember();
        member.setId(MEMBER_ID);
        member.setName("Test Member");
        member.setColor(FamilyColor.CORAL);
        member.setEmail("member@test.com");
        member.setFamily(family);
        return member;
    }

    public static CalendarEvent createCalendarEvent(Family family, FamilyMember member) {
        CalendarEvent event = new CalendarEvent();
        event.setId(EVENT_ID);
        event.setTitle("Test Event");
        event.setStartTime(LocalTime.of(9, 0));
        event.setEndTime(LocalTime.of(10, 0));
        event.setDate(LocalDate.of(2025, 6, 15));
        event.setFamily(family);
        event.setMember(member);
        event.setAllDay(false);
        event.setLocation("Test Location");
        return event;
    }

    // --- DTO factories ---

    public static RegisterRequest createRegisterRequest() {
        return new RegisterRequest(
                "testfamily",
                "password123",
                "Test Family",
                List.of(createFamilyMemberRequest())
        );
    }

    public static LoginRequest createLoginRequest() {
        return new LoginRequest("testfamily", "password123");
    }

    public static FamilyMemberRequest createFamilyMemberRequest() {
        return new FamilyMemberRequest(
                "Test Member",
                FamilyColor.CORAL,
                "member@test.com",
                null
        );
    }

    public static CalendarEventRequest createCalendarEventRequest(UUID memberId) {
        return new CalendarEventRequest(
                "Test Event",
                "9:00 AM",
                "10:00 AM",
                LocalDate.of(2025, 6, 15),
                memberId,
                false,
                "Test Location",
                null,
                null,
                null
        );
    }

    public static CalendarEventRequest createMultiDayCalendarEventRequest(UUID memberId) {
        return new CalendarEventRequest(
                "Vacation",
                "12:00 AM",
                "12:00 AM",
                LocalDate.of(2025, 3, 7),
                memberId,
                true,
                null,
                LocalDate.of(2025, 3, 9),
                null,
                null
        );
    }

    public static CalendarEventRequest createRecurringCalendarEventRequest(UUID memberId) {
        return new CalendarEventRequest(
                "Preschool",
                "9:00 AM",
                "12:00 PM",
                LocalDate.of(2025, 6, 3),
                memberId,
                false,
                "School",
                null,
                "FREQ=WEEKLY;BYDAY=TU,TH,FR",
                null
        );
    }

    public static CalendarEvent createRecurringCalendarEvent(Family family, FamilyMember member) {
        CalendarEvent event = new CalendarEvent();
        event.setId(UUID.randomUUID());
        event.setTitle("Preschool");
        event.setStartTime(LocalTime.of(9, 0));
        event.setEndTime(LocalTime.of(12, 0));
        event.setDate(LocalDate.of(2025, 6, 3));
        event.setFamily(family);
        event.setMember(member);
        event.setAllDay(false);
        event.setLocation("School");
        event.setRecurrenceRule("FREQ=WEEKLY;BYDAY=TU,TH,FR");
        return event;
    }

    public static CalendarEvent createMultiDayCalendarEvent(Family family, FamilyMember member) {
        CalendarEvent event = new CalendarEvent();
        event.setId(UUID.randomUUID());
        event.setTitle("Vacation");
        event.setStartTime(LocalTime.MIDNIGHT);
        event.setEndTime(LocalTime.MIDNIGHT);
        event.setDate(LocalDate.of(2025, 3, 7));
        event.setEndDate(LocalDate.of(2025, 3, 9));
        event.setFamily(family);
        event.setMember(member);
        event.setAllDay(true);
        return event;
    }

    public static FamilyRequest createFamilyRequest() {
        return new FamilyRequest("Updated Family", "testfamily");
    }

    public static FamilyResponse createFamilyResponse(Family family) {
        return new FamilyResponse(
                family.getId(),
                family.getName(),
                family.getFamilyMembers() != null
                        ? family.getFamilyMembers().stream()
                        .map(m -> new FamilyMemberResponse(
                                m.getId(),
                                m.getName(),
                                m.getColor(),
                                m.getEmail(),
                                m.getAvatarUrl()
                        ))
                        .toList()
                        : List.of(),
                family.getCreatedAt()
        );
    }

    public static FamilyMemberResponse createFamilyMemberResponse() {
        return new FamilyMemberResponse(
                MEMBER_ID,
                "Test Member",
                FamilyColor.CORAL,
                "member@test.com",
                null
        );
    }

    public static CalendarEventResponse createCalendarEventResponse() {
        return CalendarEventResponse.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .startTime("9:00 AM")
                .endTime("10:00 AM")
                .date(LocalDate.of(2025, 6, 15))
                .memberId(MEMBER_ID)
                .isAllDay(false)
                .location("Test Location")
                .endDate(null)
                .recurrenceRule(null)
                .recurringEventId(null)
                .isRecurring(false)
                .source("NATIVE")
                .description(null)
                .build();
    }
}
