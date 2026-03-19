package com.familyhub.demo.service;

import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.EventSource;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.model.GoogleSyncedCalendar;
import com.familyhub.demo.repository.CalendarEventRepository;
import com.familyhub.demo.repository.GoogleSyncedCalendarRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.familyhub.demo.event.SyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarSyncService {

    private final CalendarEventRepository calendarEventRepository;
    private final GoogleSyncedCalendarRepository syncedCalendarRepository;
    private final GoogleEventMapper googleEventMapper;
    private final GoogleCredentialService credentialService;

    @Lazy
    @Autowired
    private GoogleCalendarSyncService self;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSyncRequested(SyncRequestedEvent event) {
        self.syncMember(event.memberId());
    }

    @Async
    public void syncMember(UUID memberId) {
        log.info("Starting async sync for member {}", memberId);
        List<GoogleSyncedCalendar> calendars = syncedCalendarRepository.findByMemberIdAndEnabledTrue(memberId);
        if (calendars.isEmpty()) {
            log.info("No enabled calendars for member {}, skipping sync", memberId);
            return;
        }

        Calendar calendarClient = buildCalendarClient(memberId);

        FamilyMember member = calendars.getFirst().getMember();

        // Fetch events from all calendars first — abort entirely on any failure
        Map<GoogleSyncedCalendar, List<Event>> eventsByCalendar = new LinkedHashMap<>();
        for (GoogleSyncedCalendar cal : calendars) {
            try {
                eventsByCalendar.put(cal, fetchAllEvents(cal, calendarClient));
            } catch (IOException e) {
                log.error("Failed to fetch events from calendar {} for member {}: {}",
                        cal.getGoogleCalendarId(), memberId, e.getMessage());
                log.error("Aborting sync for member {} — partial failure, preserving existing events", memberId);
                return;
            }
        }

        List<Event> allEvents = eventsByCalendar.values().stream()
                .flatMap(List::stream)
                .toList();

        // DB operations — wrapped in a transaction via proxy
        self.persistSyncedEvents(member, allEvents, eventsByCalendar, calendars);
    }

    /**
     * Persists synced Google events within a single transaction.
     * Called via self-proxy so @Transactional takes effect.
     */
    @Transactional
    public void persistSyncedEvents(FamilyMember member,
                                     List<Event> allEvents,
                                     Map<GoogleSyncedCalendar, List<Event>> eventsByCalendar,
                                     List<GoogleSyncedCalendar> calendars) {
        calendarEventRepository.deleteByMemberAndSource(member, EventSource.GOOGLE);
        saveGoogleEvents(allEvents, eventsByCalendar);

        Instant now = Instant.now();
        for (GoogleSyncedCalendar cal : calendars) {
            cal.setLastSyncedAt(now);
            syncedCalendarRepository.save(cal);
        }
    }

    /**
     * Builds a Google Calendar API client for the given member.
     * Package-private for test spying.
     */
    Calendar buildCalendarClient(UUID memberId) {
        Credential credential = credentialService.getCredential(memberId);
        return new Calendar.Builder(
                credentialService.getHttpTransport(),
                credentialService.getJsonFactory(),
                credential)
                .setApplicationName("FamilyHub")
                .build();
    }

    /**
     * Sync a single calendar. Used when only one calendar needs syncing.
     * Deletes all Google events for the member first, then inserts from this calendar.
     */
    @Transactional
    public void fullSync(GoogleSyncedCalendar syncedCal, Calendar calendarClient) {
        try {
            List<Event> allEvents = fetchAllEvents(syncedCal, calendarClient);
            calendarEventRepository.deleteBySyncedCalendarAndSource(syncedCal, EventSource.GOOGLE);
            Map<GoogleSyncedCalendar, List<Event>> eventsByCalendar = Map.of(syncedCal, allEvents);
            saveGoogleEvents(allEvents, eventsByCalendar);
            syncedCal.setLastSyncedAt(Instant.now());
            syncedCalendarRepository.save(syncedCal);
        } catch (IOException e) {
            log.error("Failed to sync calendar {} for member {}: {}",
                    syncedCal.getGoogleCalendarId(), syncedCal.getMember().getId(), e.getMessage());
            throw new RuntimeException("Google Calendar sync failed", e);
        }
    }

    /**
     * Saves Google events: parents/regular first (flush), then exceptions.
     * Cancelled non-recurring events and orphaned exceptions are skipped.
     */
    private void saveGoogleEvents(List<Event> allEvents,
                                   Map<GoogleSyncedCalendar, List<Event>> eventsByCalendar) {
        List<Event> parentsAndRegular = allEvents.stream()
                .filter(e -> e.getRecurringEventId() == null)
                .filter(e -> !"cancelled".equals(e.getStatus()))
                .toList();

        List<Event> exceptions = allEvents.stream()
                .filter(e -> e.getRecurringEventId() != null)
                .toList();

        List<CalendarEvent> parentEntities = new ArrayList<>();
        for (Event event : parentsAndRegular) {
            GoogleSyncedCalendar syncedCal = resolveCalendar(event, eventsByCalendar);
            parentEntities.add(googleEventMapper.toEntity(event, syncedCal));
        }
        calendarEventRepository.saveAll(parentEntities);
        calendarEventRepository.flush();

        for (Event exception : exceptions) {
            String googleParentId = exception.getRecurringEventId();
            Optional<CalendarEvent> parentEntity = calendarEventRepository.findByGoogleEventId(googleParentId);

            if (parentEntity.isEmpty()) {
                log.warn("Orphaned exception {} — parent {} not found, skipping",
                        exception.getId(), googleParentId);
                continue;
            }

            GoogleSyncedCalendar syncedCal = resolveCalendar(exception, eventsByCalendar);
            CalendarEvent exceptionEntity = googleEventMapper.toExceptionEntity(
                    exception, syncedCal, parentEntity.get());
            calendarEventRepository.save(exceptionEntity);
        }
    }

    /**
     * Fetches only events that changed since last sync using Google's sync token.
     * Does NOT use setSingleEvents or setMaxResults — incompatible with sync tokens.
     * Package-private for test access.
     */
    List<Event> fetchIncrementalEvents(GoogleSyncedCalendar syncedCal, Calendar calendarClient) throws IOException {
        String pageToken = null;
        List<Event> changedEvents = new ArrayList<>();

        do {
            Events response = calendarClient.events().list(syncedCal.getGoogleCalendarId())
                    .setSyncToken(syncedCal.getSyncToken())
                    .setPageToken(pageToken)
                    .execute();

            if (response.getItems() != null) {
                changedEvents.addAll(response.getItems());
            }
            pageToken = response.getNextPageToken();

            if (pageToken == null && response.getNextSyncToken() != null) {
                syncedCal.setSyncToken(response.getNextSyncToken());
            }
        } while (pageToken != null);

        return changedEvents;
    }

    /**
     * Copies mutable fields from updated entity onto existing entity.
     * Preserves: id, googleEventId, source, member, family, syncedCalendar, recurringEvent, originalDate.
     * DD-3: Field list must stay in sync with CalendarEvent entity changes.
     */
    void updateExistingEvent(CalendarEvent existing, CalendarEvent updated) {
        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setLocation(updated.getLocation());
        existing.setDate(updated.getDate());
        existing.setEndDate(updated.getEndDate());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setAllDay(updated.isAllDay());
        existing.setHtmlLink(updated.getHtmlLink());
        existing.setEtag(updated.getEtag());
        existing.setGoogleUpdatedAt(updated.getGoogleUpdatedAt());
        existing.setRecurrenceRule(updated.getRecurrenceRule());
        existing.setExdates(updated.getExdates());
        existing.setCancelled(updated.isCancelled());
    }

    private List<Event> fetchAllEvents(GoogleSyncedCalendar syncedCal, Calendar calendarClient) throws IOException {
        String pageToken = null;
        List<Event> allEvents = new ArrayList<>();

        do {
            Events response = calendarClient.events().list(syncedCal.getGoogleCalendarId())
                    .setSingleEvents(false)
                    .setMaxResults(250)
                    .setPageToken(pageToken)
                    .execute();

            if (response.getItems() != null) {
                allEvents.addAll(response.getItems());
            }
            pageToken = response.getNextPageToken();

            if (pageToken == null && response.getNextSyncToken() != null) {
                syncedCal.setSyncToken(response.getNextSyncToken());
            }
        } while (pageToken != null);

        return allEvents;
    }

    private GoogleSyncedCalendar resolveCalendar(Event event,
                                                  Map<GoogleSyncedCalendar, List<Event>> eventsByCalendar) {
        for (Map.Entry<GoogleSyncedCalendar, List<Event>> entry : eventsByCalendar.entrySet()) {
            if (entry.getValue().contains(event)) {
                return entry.getKey();
            }
        }
        log.warn("Could not resolve calendar for event {} — falling back to first calendar", event.getId());
        return eventsByCalendar.keySet().iterator().next();
    }
}
