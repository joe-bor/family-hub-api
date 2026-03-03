package com.familyhub.demo.service;

import com.familyhub.demo.dto.CalendarEventRequest;
import com.familyhub.demo.dto.CalendarEventResponse;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.repository.CalendarEventRepository;
import com.familyhub.demo.repository.FamilyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @InjectMocks
    private CalendarEventService calendarEventService;

    private Family family;
    private FamilyMember member;
    private CalendarEvent event;

    @BeforeEach
    void setUp() {
        family = createFamily();
        family.setFamilyMembers(List.of());
        member = createFamilyMember(family);
        event = createCalendarEvent(family, member);
    }

    @Test
    void getAllEventsByFamily_unfiltered_returnsAll() {
        CalendarEvent event2 = createCalendarEvent(family, member);
        event2.setId(UUID.randomUUID());
        event2.setTitle("Second Event");
        when(calendarEventRepository.findByFamily(family)).thenReturn(List.of(event, event2));

        List<CalendarEventResponse> result = calendarEventService.getAllEventsByFamily(family, null, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllEventsByFamily_filteredByDateRange_returnsFiltered() {
        CalendarEvent outsideRange = createCalendarEvent(family, member);
        outsideRange.setId(UUID.randomUUID());
        outsideRange.setDate(LocalDate.of(2025, 1, 1));

        when(calendarEventRepository.findByFamily(family)).thenReturn(List.of(event, outsideRange));

        List<CalendarEventResponse> result = calendarEventService.getAllEventsByFamily(
                family, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30), null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Test Event");
    }

    @Test
    void getAllEventsByFamily_filteredByMemberId_returnsFiltered() {
        FamilyMember otherMember = createFamilyMember(family);
        UUID otherMemberId = UUID.randomUUID();
        otherMember.setId(otherMemberId);

        CalendarEvent otherEvent = createCalendarEvent(family, otherMember);
        otherEvent.setId(UUID.randomUUID());

        when(calendarEventRepository.findByFamily(family)).thenReturn(List.of(event, otherEvent));
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        List<CalendarEventResponse> result = calendarEventService.getAllEventsByFamily(
                family, null, null, MEMBER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().memberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void getAllEventsByFamily_memberFromWrongFamily_throwsAccessDenied() {
        Family otherFamily = createOtherFamily();
        FamilyMember wrongMember = createFamilyMember(otherFamily);
        UUID wrongMemberId = UUID.randomUUID();
        wrongMember.setId(wrongMemberId);

        when(familyMemberRepository.findById(wrongMemberId)).thenReturn(Optional.of(wrongMember));

        assertThatThrownBy(() -> calendarEventService.getAllEventsByFamily(family, null, null, wrongMemberId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getEventById_found_returnsEvent() {
        when(calendarEventRepository.findByFamilyAndId(family, EVENT_ID)).thenReturn(Optional.of(event));

        CalendarEventResponse result = calendarEventService.getEventById(EVENT_ID, family);

        assertThat(result.id()).isEqualTo(EVENT_ID);
        assertThat(result.title()).isEqualTo("Test Event");
    }

    @Test
    void getEventById_notFound_throwsResourceNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(calendarEventRepository.findByFamilyAndId(family, unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarEventService.getEventById(unknownId, family))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addCalendarEvent_success_returnsSavedEvent() {
        CalendarEventRequest request = createCalendarEventRequest(MEMBER_ID);
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenReturn(event);

        CalendarEventResponse result = calendarEventService.addCalendarEvent(request, family);

        assertThat(result.id()).isEqualTo(EVENT_ID);
        verify(calendarEventRepository).save(any(CalendarEvent.class));
    }

    @Test
    void addCalendarEvent_invalidTimeRange_throwsBadRequest() {
        CalendarEventRequest request = new CalendarEventRequest(
                "Bad Event", "10:00 AM", "9:00 AM",
                LocalDate.of(2025, 6, 15), MEMBER_ID, false, null);
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> calendarEventService.addCalendarEvent(request, family))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Start time must be before end time");
    }

    @Test
    void addCalendarEvent_allDayEvent_skipsTimeValidation() {
        CalendarEventRequest request = new CalendarEventRequest(
                "Birthday", "12:00 AM", "12:00 AM",
                LocalDate.of(2025, 6, 15), MEMBER_ID, true, null);

        CalendarEvent allDayEvent = createCalendarEvent(family, member);
        allDayEvent.setAllDay(true);
        allDayEvent.setStartTime(LocalTime.MIDNIGHT);
        allDayEvent.setEndTime(LocalTime.MIDNIGHT);

        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenReturn(allDayEvent);

        CalendarEventResponse result = calendarEventService.addCalendarEvent(request, family);

        assertThat(result.isAllDay()).isTrue();
    }

    @Test
    void addCalendarEvent_memberFromWrongFamily_throwsAccessDenied() {
        Family otherFamily = createOtherFamily();
        FamilyMember wrongMember = createFamilyMember(otherFamily);
        UUID wrongMemberId = UUID.randomUUID();
        wrongMember.setId(wrongMemberId);

        CalendarEventRequest request = createCalendarEventRequest(wrongMemberId);
        when(familyMemberRepository.findById(wrongMemberId)).thenReturn(Optional.of(wrongMember));

        assertThatThrownBy(() -> calendarEventService.addCalendarEvent(request, family))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateCalendarEvent_success_verifySaveCalled() {
        CalendarEventRequest request = createCalendarEventRequest(MEMBER_ID);
        when(familyMemberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(calendarEventRepository.findByFamilyAndId(family, EVENT_ID)).thenReturn(Optional.of(event));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenReturn(event);

        calendarEventService.updateCalendarEvent(request, EVENT_ID, family);

        verify(calendarEventRepository).save(any(CalendarEvent.class));
    }

    @Test
    void deleteCalendarEvent_success() {
        when(calendarEventRepository.findByFamilyAndId(family, EVENT_ID)).thenReturn(Optional.of(event));

        calendarEventService.deleteCalendarEvent(EVENT_ID, family);

        verify(calendarEventRepository).delete(event);
    }

    @Test
    void deleteCalendarEvent_notFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(calendarEventRepository.findByFamilyAndId(family, unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarEventService.deleteCalendarEvent(unknownId, family))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
