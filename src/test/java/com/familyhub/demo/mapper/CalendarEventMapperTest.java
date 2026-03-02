package com.familyhub.demo.mapper;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.dto.CalendarEventRequest;
import com.familyhub.demo.dto.CalendarEventResponse;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarEventMapperTest {

    @Test
    void toEntity_parsesTimeCorrectly() {
        Family family = TestDataFactory.createFamily();
        FamilyMember member = TestDataFactory.createFamilyMember(family);
        CalendarEventRequest request = new CalendarEventRequest(
                "Meeting", "2:00 PM", "3:30 PM",
                LocalDate.of(2026, 3, 15), member.getId(), false, "Office");

        CalendarEvent entity = CalendarEventMapper.toEntity(request, family, member);

        assertThat(entity.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(entity.getEndTime()).isEqualTo(LocalTime.of(15, 30));
        assertThat(entity.getTitle()).isEqualTo("Meeting");
        assertThat(entity.getFamily()).isEqualTo(family);
        assertThat(entity.getMember()).isEqualTo(member);
        assertThat(entity.getLocation()).isEqualTo("Office");
        assertThat(entity.isAllDay()).isFalse();
    }

    @Test
    void toEntity_allDayEvent() {
        Family family = TestDataFactory.createFamily();
        FamilyMember member = TestDataFactory.createFamilyMember(family);
        CalendarEventRequest request = new CalendarEventRequest(
                "Birthday", "12:00 AM", "12:00 AM",
                LocalDate.of(2026, 3, 15), member.getId(), true, null);

        CalendarEvent entity = CalendarEventMapper.toEntity(request, family, member);

        assertThat(entity.isAllDay()).isTrue();
    }

    @Test
    void toEntity_nullIsAllDay_defaultsFalse() {
        Family family = TestDataFactory.createFamily();
        FamilyMember member = TestDataFactory.createFamilyMember(family);
        CalendarEventRequest request = new CalendarEventRequest(
                "Event", "9:00 AM", "10:00 AM",
                LocalDate.of(2026, 3, 15), member.getId(), null, null);

        CalendarEvent entity = CalendarEventMapper.toEntity(request, family, member);

        assertThat(entity.isAllDay()).isFalse();
    }

    @Test
    void toDto_formatsTo12HourString() {
        Family family = TestDataFactory.createFamily();
        FamilyMember member = TestDataFactory.createFamilyMember(family);
        CalendarEvent event = TestDataFactory.createCalendarEvent(family, member);

        CalendarEventResponse dto = CalendarEventMapper.toDto(event);

        assertThat(dto.startTime()).isEqualTo("9:00 AM");
        assertThat(dto.endTime()).isEqualTo("10:00 AM");
        assertThat(dto.id()).isEqualTo(event.getId());
        assertThat(dto.memberId()).isEqualTo(member.getId());
    }

    @Test
    void toEntity_invalidTimeString_throwsBadRequest() {
        Family family = TestDataFactory.createFamily();
        FamilyMember member = TestDataFactory.createFamilyMember(family);
        CalendarEventRequest request = new CalendarEventRequest(
                "Bad", "not-a-time", "10:00 AM",
                LocalDate.of(2026, 3, 15), member.getId(), false, null);

        assertThatThrownBy(() -> CalendarEventMapper.toEntity(request, family, member))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Error Parsing");
    }
}
