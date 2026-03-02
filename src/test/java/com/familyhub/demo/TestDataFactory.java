package com.familyhub.demo;

import com.familyhub.demo.dto.*;
import com.familyhub.demo.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static final UUID FAMILY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID OTHER_FAMILY_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    public static Family createFamily() {
        Family family = new Family();
        family.setId(FAMILY_ID);
        family.setName("Test Family");
        family.setUsername("testfamily");
        family.setPasswordHash("encoded-password");
        family.setCreatedAt(LocalDateTime.now());
        family.setUpdatedAt(LocalDateTime.now());
        family.setFamilyMembers(new ArrayList<>());
        return family;
    }

    public static Family createOtherFamily() {
        Family family = new Family();
        family.setId(OTHER_FAMILY_ID);
        family.setName("Other Family");
        family.setUsername("otherfamily");
        family.setPasswordHash("encoded-password");
        family.setFamilyMembers(new ArrayList<>());
        return family;
    }

    public static FamilyMember createFamilyMember(Family family) {
        FamilyMember member = new FamilyMember();
        member.setId(MEMBER_ID);
        member.setFamily(family);
        member.setName("John");
        member.setColor(FamilyColor.CORAL);
        member.setEmail("john@test.com");
        return member;
    }

    public static CalendarEvent createCalendarEvent(Family family, FamilyMember member) {
        CalendarEvent event = new CalendarEvent();
        event.setId(EVENT_ID);
        event.setTitle("Test Event");
        event.setStartTime(LocalTime.of(9, 0));
        event.setEndTime(LocalTime.of(10, 0));
        event.setDate(LocalDate.of(2026, 3, 15));
        event.setFamily(family);
        event.setMember(member);
        event.setAllDay(false);
        event.setLocation("Home");
        return event;
    }

    public static RegisterRequest createRegisterRequest() {
        return new RegisterRequest(
                "testfamily",
                "password123",
                "Test Family",
                List.of(new FamilyMemberRequest("John", FamilyColor.CORAL, null, null))
        );
    }

    public static LoginRequest createLoginRequest() {
        return new LoginRequest("testfamily", "password123");
    }

    public static FamilyMemberRequest createFamilyMemberRequest() {
        return new FamilyMemberRequest("John", FamilyColor.CORAL, "john@test.com", null);
    }

    public static CalendarEventRequest createCalendarEventRequest(UUID memberId) {
        return new CalendarEventRequest(
                "Test Event",
                "9:00 AM",
                "10:00 AM",
                LocalDate.of(2026, 3, 15),
                memberId,
                false,
                "Home"
        );
    }

    public static FamilyRequest createFamilyRequest() {
        return new FamilyRequest("Updated Family", "updatedfamily");
    }

    public static FamilyResponse createFamilyResponse(Family family) {
        return new FamilyResponse(
                family.getId(),
                family.getName(),
                List.of(),
                family.getCreatedAt()
        );
    }

    public static FamilyMemberResponse createFamilyMemberResponse() {
        return new FamilyMemberResponse(MEMBER_ID, "John", FamilyColor.CORAL, "john@test.com", null);
    }

    public static CalendarEventResponse createCalendarEventResponse() {
        return CalendarEventResponse.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .startTime("9:00 AM")
                .endTime("10:00 AM")
                .date(LocalDate.of(2026, 3, 15))
                .memberId(MEMBER_ID)
                .isAllDay(false)
                .location("Home")
                .build();
    }
}
