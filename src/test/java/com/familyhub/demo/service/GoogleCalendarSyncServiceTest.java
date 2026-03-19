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
import org.springframework.test.util.ReflectionTestUtils;
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

    @Spy
    @InjectMocks
    private GoogleCalendarSyncService syncService;

    private GoogleSyncedCalendar syncedCal;
    private FamilyMember member;
    private Family family;

    @BeforeEach
    void setUp() {
        // Wire the self-proxy to the spy so syncMember -> persistSyncedEvents works in unit tests
        ReflectionTestUtils.setField(syncService, "self", syncService);

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
    void fullSync_deletesThisCalendarsEventsOnly() throws IOException {
        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(new ArrayList<>());
        response.setNextSyncToken("sync-token-1");

        Calendar calendarClient = mockCalendarClient(response);
        syncService.fullSync(syncedCal, calendarClient);

        verify(calendarEventRepository).deleteBySyncedCalendarAndSource(syncedCal, EventSource.GOOGLE);
        verify(calendarEventRepository, never()).deleteByMemberAndSource(any(), any());
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
        inOrder.verify(calendarEventRepository).deleteBySyncedCalendarAndSource(syncedCal, EventSource.GOOGLE);
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

    @Test
    void fullSync_singleCalendar_deletesOnceAndInserts() throws IOException {
        // Verify fullSync for a single calendar does one delete + insert
        Event eventA = createTimedGoogleEvent("event-a", "Meeting",
                "2025-06-15T09:00:00-04:00", "2025-06-15T10:00:00-04:00");

        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        response.setItems(java.util.List.of(eventA));
        response.setNextSyncToken("token-a");

        Calendar calendarClient = mockCalendarClient(response);

        CalendarEvent mappedEntity = new CalendarEvent();
        when(googleEventMapper.toEntity(eq(eventA), eq(syncedCal))).thenReturn(mappedEntity);

        syncService.fullSync(syncedCal, calendarClient);

        verify(calendarEventRepository, times(1)).deleteBySyncedCalendarAndSource(syncedCal, EventSource.GOOGLE);
        verify(calendarEventRepository).saveAll(argThat(list ->
                ((java.util.List<?>) list).contains(mappedEntity)));
    }

    @Test
    void syncMember_anyCalendarFetchFails_abortsEntireSync() throws IOException {
        GoogleSyncedCalendar cal1 = createSyncedCalendar("cal-1");
        GoogleSyncedCalendar cal2 = createSyncedCalendar("cal-2");

        when(syncedCalendarRepository.findByMemberIdAndEnabledTrue(member.getId()))
                .thenReturn(java.util.List.of(cal1, cal2));

        com.google.api.services.calendar.model.Events successResponse = new com.google.api.services.calendar.model.Events();
        successResponse.setItems(new ArrayList<>());
        successResponse.setNextSyncToken("token");

        Calendar calendarClient = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.List listRequest1 = mock(Calendar.Events.List.class);
        Calendar.Events.List listRequest2 = mock(Calendar.Events.List.class);

        when(calendarClient.events()).thenReturn(events);
        when(events.list("cal-1")).thenReturn(listRequest1);
        when(events.list("cal-2")).thenReturn(listRequest2);

        when(listRequest1.setSingleEvents(false)).thenReturn(listRequest1);
        when(listRequest1.setMaxResults(250)).thenReturn(listRequest1);
        when(listRequest1.setPageToken(null)).thenReturn(listRequest1);
        when(listRequest1.execute()).thenReturn(successResponse);

        when(listRequest2.setSingleEvents(false)).thenReturn(listRequest2);
        when(listRequest2.setMaxResults(250)).thenReturn(listRequest2);
        when(listRequest2.setPageToken(null)).thenReturn(listRequest2);
        when(listRequest2.execute()).thenThrow(new IOException("Token expired"));

        doReturn(calendarClient).when(syncService).buildCalendarClient(member.getId());

        syncService.syncMember(member.getId());

        verify(calendarEventRepository, never()).deleteByMemberAndSource(any(), any());
        verify(calendarEventRepository, never()).saveAll(any());
    }

    @Test
    void fetchIncrementalEvents_usesSyncTokenAndStoresNewToken() throws IOException {
        syncedCal.setSyncToken("old-sync-token");

        com.google.api.services.calendar.model.Events response = new com.google.api.services.calendar.model.Events();
        Event changedEvent = createTimedGoogleEvent("evt-1", "Updated Meeting",
                "2025-06-15T09:00:00-04:00", "2025-06-15T10:00:00-04:00");
        response.setItems(java.util.List.of(changedEvent));
        response.setNextSyncToken("new-sync-token");

        Calendar calendarClient = mockIncrementalCalendarClient(response);

        java.util.List<Event> result = syncService.fetchIncrementalEvents(syncedCal, calendarClient);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo("evt-1");
        assertThat(syncedCal.getSyncToken()).isEqualTo("new-sync-token");

        // Verify sync token was used, NOT setSingleEvents
        Calendar.Events events = calendarClient.events();
        Calendar.Events.List listReq = events.list(anyString());
        verify(listReq).setSyncToken("old-sync-token");
        verify(listReq, never()).setSingleEvents(anyBoolean());
        verify(listReq, never()).setMaxResults(anyInt());
    }

    @Test
    void updateExistingEvent_copiesMutableFieldsOnly() {
        CalendarEvent existing = new CalendarEvent();
        existing.setId(UUID.randomUUID());
        existing.setGoogleEventId("google-123");
        existing.setSource(EventSource.GOOGLE);
        existing.setMember(member);
        existing.setFamily(family);
        existing.setSyncedCalendar(syncedCal);
        existing.setTitle("Old Title");
        existing.setDescription("Old Desc");
        existing.setStartTime(java.time.LocalTime.of(9, 0));
        existing.setEndTime(java.time.LocalTime.of(10, 0));
        existing.setDate(java.time.LocalDate.of(2025, 6, 15));

        CalendarEvent updated = new CalendarEvent();
        updated.setTitle("New Title");
        updated.setDescription("New Desc");
        updated.setLocation("Room 42");
        updated.setDate(java.time.LocalDate.of(2025, 6, 16));
        updated.setStartTime(java.time.LocalTime.of(10, 0));
        updated.setEndTime(java.time.LocalTime.of(11, 0));
        updated.setAllDay(false);
        updated.setHtmlLink("https://calendar.google.com/updated");
        updated.setEtag("\"etag-2\"");
        updated.setGoogleUpdatedAt(java.time.Instant.now());
        updated.setRecurrenceRule("FREQ=WEEKLY");
        updated.setExdates("2025-06-23");
        updated.setCancelled(false);

        UUID originalId = existing.getId();
        FamilyMember originalMember = existing.getMember();

        syncService.updateExistingEvent(existing, updated);

        // Mutable fields copied
        assertThat(existing.getTitle()).isEqualTo("New Title");
        assertThat(existing.getDescription()).isEqualTo("New Desc");
        assertThat(existing.getLocation()).isEqualTo("Room 42");
        assertThat(existing.getDate()).isEqualTo(java.time.LocalDate.of(2025, 6, 16));
        assertThat(existing.getStartTime()).isEqualTo(java.time.LocalTime.of(10, 0));
        assertThat(existing.getEndTime()).isEqualTo(java.time.LocalTime.of(11, 0));
        assertThat(existing.getHtmlLink()).isEqualTo("https://calendar.google.com/updated");
        assertThat(existing.getEtag()).isEqualTo("\"etag-2\"");
        assertThat(existing.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY");
        assertThat(existing.getExdates()).isEqualTo("2025-06-23");
        assertThat(existing.getEndDate()).isEqualTo(updated.getEndDate());

        // Immutable fields preserved
        assertThat(existing.getId()).isEqualTo(originalId);
        assertThat(existing.getGoogleEventId()).isEqualTo("google-123");
        assertThat(existing.getSource()).isEqualTo(EventSource.GOOGLE);
        assertThat(existing.getMember()).isSameAs(originalMember);
        assertThat(existing.getFamily()).isSameAs(family);
        assertThat(existing.getSyncedCalendar()).isSameAs(syncedCal);
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

    private GoogleSyncedCalendar createSyncedCalendar(String googleCalId) {
        GoogleSyncedCalendar cal = new GoogleSyncedCalendar();
        cal.setId(UUID.randomUUID());
        cal.setMember(member);
        cal.setToken(syncedCal.getToken());
        cal.setGoogleCalendarId(googleCalId);
        cal.setEnabled(true);
        return cal;
    }

    private Calendar mockIncrementalCalendarClient(com.google.api.services.calendar.model.Events response) throws IOException {
        Calendar calendarClient = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.List listRequest = mock(Calendar.Events.List.class);

        when(calendarClient.events()).thenReturn(events);
        when(events.list(anyString())).thenReturn(listRequest);
        when(listRequest.setSyncToken(anyString())).thenReturn(listRequest);
        when(listRequest.setPageToken(any())).thenReturn(listRequest);
        when(listRequest.execute()).thenReturn(response);

        return calendarClient;
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
