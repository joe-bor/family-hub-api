package com.familyhub.demo.service;

import com.familyhub.demo.model.*;
import com.familyhub.demo.repository.CalendarEventRepository;
import com.familyhub.demo.repository.GoogleSyncedCalendarRepository;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.Calendar.Events;
import com.google.api.services.calendar.Calendar.Events.List;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarSyncServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;
    @Mock
    private GoogleSyncedCalendarRepository syncedCalendarRepository;
    @Mock
    private GoogleEventMapper googleEventMapper;
    @Mock
    private GoogleCredentialService credentialService;

    @InjectMocks
    private GoogleCalendarSyncService syncService;

    private GoogleSyncedCalendar syncedCal;
    private FamilyMember member;
    private Family family;

    @BeforeEach
    void setUp() {
        family = new Family();
        family.setId(UUID.randomUUID());

        member = new FamilyMember();
        member.setId(UUID.randomUUID());
        member.setFamily(family);

        GoogleOAuthToken token = new GoogleOAuthToken();
        token.setMember(member);

        syncedCal = new GoogleSyncedCalendar();
        syncedCal.setId(UUID.randomUUID());
        syncedCal.setMember(member);
        syncedCal.setToken(token);
        syncedCal.setGoogleCalendarId("primary");
        syncedCal.setEnabled(true);
    }

    @Test
    void fullSync_deletesExistingGoogleEventsFirst() throws IOException {
        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(new ArrayList<>());
        response.setNextSyncToken("sync-token-1");

        Calendar calendarClient = mockCalendarClient(response);
        syncService.fullSync(syncedCal, calendarClient);

        verify(calendarEventRepository).deleteByMemberAndSource(member, EventSource.GOOGLE);
    }

    @Test
    void fullSync_savesParentsBeforeExceptions() throws IOException {
        Event parentEvent = createTimedGoogleEvent("parent-123", "Standup",
                "2025-06-03T09:00:00-04:00", "2025-06-03T09:30:00-04:00");
        parentEvent.setRecurrence(java.util.List.of("RRULE:FREQ=WEEKLY;BYDAY=TU"));

        Event exceptionEvent = createTimedGoogleEvent("parent-123_20250610T130000Z", "Rescheduled",
                "2025-06-10T10:00:00-04:00", "2025-06-10T10:30:00-04:00");
        exceptionEvent.setRecurringEventId("parent-123");
        exceptionEvent.setOriginalStartTime(new EventDateTime()
                .setDateTime(new DateTime("2025-06-10T09:00:00-04:00")));

        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(java.util.List.of(parentEvent, exceptionEvent));
        response.setNextSyncToken("sync-token-1");

        Calendar calendarClient = mockCalendarClient(response);

        CalendarEvent parentEntity = new CalendarEvent();
        parentEntity.setId(UUID.randomUUID());
        when(googleEventMapper.toEntity(eq(parentEvent), eq(syncedCal))).thenReturn(parentEntity);

        CalendarEvent exceptionEntity = new CalendarEvent();
        when(googleEventMapper.toExceptionEntity(eq(exceptionEvent), eq(syncedCal), eq(parentEntity)))
                .thenReturn(exceptionEntity);

        when(calendarEventRepository.findByGoogleEventId("parent-123"))
                .thenReturn(Optional.of(parentEntity));

        syncService.fullSync(syncedCal, calendarClient);

        InOrder inOrder = inOrder(calendarEventRepository);
        inOrder.verify(calendarEventRepository).deleteByMemberAndSource(member, EventSource.GOOGLE);
        inOrder.verify(calendarEventRepository).saveAll(argThat(list ->
                ((java.util.List<?>) list).contains(parentEntity)));
        inOrder.verify(calendarEventRepository).flush();
        inOrder.verify(calendarEventRepository).save(exceptionEntity);
    }

    @Test
    void fullSync_skipsOrphanedExceptions() throws IOException {
        Event exceptionEvent = createTimedGoogleEvent("orphan_20250610T130000Z", "Orphan",
                "2025-06-10T10:00:00-04:00", "2025-06-10T10:30:00-04:00");
        exceptionEvent.setRecurringEventId("nonexistent-parent");
        exceptionEvent.setOriginalStartTime(new EventDateTime()
                .setDateTime(new DateTime("2025-06-10T09:00:00-04:00")));

        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(java.util.List.of(exceptionEvent));
        response.setNextSyncToken("sync-token-1");

        Calendar calendarClient = mockCalendarClient(response);

        when(calendarEventRepository.findByGoogleEventId("nonexistent-parent"))
                .thenReturn(Optional.empty());

        syncService.fullSync(syncedCal, calendarClient);

        verify(googleEventMapper, never()).toExceptionEntity(any(), any(), any());
    }

    @Test
    void fullSync_skipsCancelledNonRecurringEvents() throws IOException {
        Event cancelledEvent = createTimedGoogleEvent("cancelled-123", "Cancelled",
                "2025-06-15T09:00:00-04:00", "2025-06-15T10:00:00-04:00");
        cancelledEvent.setStatus("cancelled");

        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(java.util.List.of(cancelledEvent));
        response.setNextSyncToken("sync-token-1");

        Calendar calendarClient = mockCalendarClient(response);

        syncService.fullSync(syncedCal, calendarClient);

        verify(googleEventMapper, never()).toEntity(any(), any());
    }

    @Test
    void fullSync_storesSyncToken() throws IOException {
        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(new ArrayList<>());
        response.setNextSyncToken("new-sync-token");

        Calendar calendarClient = mockCalendarClient(response);

        syncService.fullSync(syncedCal, calendarClient);

        assertThat(syncedCal.getSyncToken()).isEqualTo("new-sync-token");
        verify(syncedCalendarRepository).save(syncedCal);
    }

    @Test
    void fullSync_updatesLastSyncedAt() throws IOException {
        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(new ArrayList<>());
        response.setNextSyncToken("sync-token");

        Calendar calendarClient = mockCalendarClient(response);

        syncService.fullSync(syncedCal, calendarClient);

        assertThat(syncedCal.getLastSyncedAt()).isNotNull();
    }

    @Test
    void syncMember_skipsWhenNoEnabledCalendars() {
        when(syncedCalendarRepository.findByMemberIdAndEnabledTrue(member.getId()))
                .thenReturn(java.util.List.of());

        syncService.syncMember(member.getId());

        verifyNoInteractions(credentialService);
    }

    // --- Helpers ---

    private Event createTimedGoogleEvent(String id, String summary, String startIso, String endIso) {
        Event event = new Event();
        event.setId(id);
        event.setSummary(summary);
        event.setUpdated(new DateTime(1700000000000L));
        event.setStart(new EventDateTime().setDateTime(new DateTime(startIso)));
        event.setEnd(new EventDateTime().setDateTime(new DateTime(endIso)));
        return event;
    }

    private Calendar mockCalendarClient(com.google.api.services.calendar.model.Events response) throws IOException {
        Calendar calendarClient = mock(Calendar.class);
        Events events = mock(Events.class);
        List listRequest = mock(List.class);

        when(calendarClient.events()).thenReturn(events);
        when(events.list(anyString())).thenReturn(listRequest);
        when(listRequest.setSingleEvents(false)).thenReturn(listRequest);
        when(listRequest.setMaxResults(250)).thenReturn(listRequest);
        when(listRequest.setPageToken(null)).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(response);

        return calendarClient;
    }
}
