package com.familyhub.demo.service;

import com.familyhub.demo.TestDataFactory;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        family = TestDataFactory.createFamily();
        member = TestDataFactory.createFamilyMember(family);
        event = TestDataFactory.createCalendarEvent(family, member);
    }

    @Test
    void getAllEventsByFamily_unfiltered() {
        when(calendarEventRepository.findByFamily(family)).thenReturn(List.of(event));

        List<CalendarEventResponse> result =
                calendarEventService.getAllEventsByFamily(family, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Test Event");
    }

    @Test
    void getAllEventsByFamily_filterByDateRange() {
        when(calendarEventRepository.findByFamily(family)).thenReturn(List.of(event));

        List<CalendarEventResponse> result = calendarEventService.getAllEventsByFamily(
                family, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllEventsByFamily_filterByDateRange_excludes() {
        when(calendarEventRepository.findByFamily(family)).thenReturn(List.of(event));

        List<CalendarEventResponse> result = calendarEventService.getAllEventsByFamily(
                family, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllEventsByFamily_filterByMemberId() {
        when(calendarEventRepository.findByFamily(family)).thenReturn(List.of(event));
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        List<CalendarEventResponse> result = calendarEventService.getAllEventsByFamily(
                family, null, null, member.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllEventsByFamily_memberFromWrongFamily_throws() {
        Family otherFamily = TestDataFactory.createOtherFamily();
        FamilyMember otherMember = TestDataFactory.createFamilyMember(otherFamily);
        when(familyMemberRepository.findById(otherMember.getId())).thenReturn(Optional.of(otherMember));

        assertThatThrownBy(() -> calendarEventService.getAllEventsByFamily(
                family, null, null, otherMember.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getEventById_success() {
        when(calendarEventRepository.findByFamilyAndId(family, event.getId()))
                .thenReturn(Optional.of(event));

        CalendarEventResponse result = calendarEventService.getEventById(event.getId(), family);

        assertThat(result.title()).isEqualTo("Test Event");
    }

    @Test
    void getEventById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(calendarEventRepository.findByFamilyAndId(family, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarEventService.getEventById(id, family))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addCalendarEvent_success() {
        CalendarEventRequest request = TestDataFactory.createCalendarEventRequest(member.getId());
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenReturn(event);

        CalendarEventResponse result = calendarEventService.addCalendarEvent(request, family);

        assertThat(result.title()).isEqualTo("Test Event");
        verify(calendarEventRepository).save(any(CalendarEvent.class));
    }

    @Test
    void addCalendarEvent_invalidTimeRange_throws() {
        CalendarEventRequest request = new CalendarEventRequest(
                "Bad Event", "10:00 AM", "9:00 AM",
                LocalDate.of(2026, 3, 15), member.getId(), false, null);
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> calendarEventService.addCalendarEvent(request, family))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start time must be before end time");
    }

    @Test
    void addCalendarEvent_allDay_skipsTimeValidation() {
        CalendarEventRequest request = new CalendarEventRequest(
                "Birthday", "12:00 AM", "12:00 AM",
                LocalDate.of(2026, 3, 15), member.getId(), true, null);
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenReturn(event);

        CalendarEventResponse result = calendarEventService.addCalendarEvent(request, family);

        assertThat(result).isNotNull();
    }

    @Test
    void addCalendarEvent_memberFromWrongFamily_throws() {
        Family otherFamily = TestDataFactory.createOtherFamily();
        FamilyMember otherMember = TestDataFactory.createFamilyMember(otherFamily);
        CalendarEventRequest request = TestDataFactory.createCalendarEventRequest(otherMember.getId());
        when(familyMemberRepository.findById(otherMember.getId())).thenReturn(Optional.of(otherMember));

        assertThatThrownBy(() -> calendarEventService.addCalendarEvent(request, family))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateCalendarEvent_success() {
        CalendarEventRequest request = TestDataFactory.createCalendarEventRequest(member.getId());
        when(familyMemberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(calendarEventRepository.findByFamilyAndId(family, event.getId()))
                .thenReturn(Optional.of(event));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenReturn(event);

        CalendarEventResponse result = calendarEventService.updateCalendarEvent(request, event.getId(), family);

        assertThat(result).isNotNull();
        verify(calendarEventRepository).save(any(CalendarEvent.class));
    }

    @Test
    void deleteCalendarEvent_success() {
        when(calendarEventRepository.findByFamilyAndId(family, event.getId()))
                .thenReturn(Optional.of(event));

        calendarEventService.deleteCalendarEvent(event.getId(), family);

        verify(calendarEventRepository).delete(event);
    }

    @Test
    void deleteCalendarEvent_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(calendarEventRepository.findByFamilyAndId(family, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarEventService.deleteCalendarEvent(id, family))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
