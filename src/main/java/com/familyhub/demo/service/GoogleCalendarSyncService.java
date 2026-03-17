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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Transactional
    public void syncMember(UUID memberId) {
        List<GoogleSyncedCalendar> calendars = syncedCalendarRepository.findByMemberIdAndEnabledTrue(memberId);
        if (calendars.isEmpty()) {
            log.info("No enabled calendars for member {}, skipping sync", memberId);
            return;
        }

        Credential credential = credentialService.getCredential(memberId);
        Calendar calendarClient = new Calendar.Builder(
                credentialService.getHttpTransport(),
                credentialService.getJsonFactory(),
                credential)
                .setApplicationName("FamilyHub")
                .build();

        FamilyMember member = calendars.getFirst().getMember();

        // Fetch events from all calendars first
        List<Event> allEvents = new ArrayList<>();
        for (GoogleSyncedCalendar cal : calendars) {
            try {
                allEvents.addAll(fetchAllEvents(cal, calendarClient));
            } catch (IOException e) {
                log.error("Failed to fetch events from calendar {} for member {}: {}",
                        cal.getGoogleCalendarId(), memberId, e.getMessage());
            }
        }

        // Single delete for the member, then bulk insert
        calendarEventRepository.deleteByMemberAndSource(member, EventSource.GOOGLE);

        // Separate parents/regular from exceptions
        List<Event> parentsAndRegular = allEvents.stream()
                .filter(e -> e.getRecurringEventId() == null)
                .filter(e -> !"cancelled".equals(e.getStatus()))
                .toList();

        List<Event> exceptions = allEvents.stream()
                .filter(e -> e.getRecurringEventId() != null)
                .toList();

        // Save parents/regular first — need to resolve which syncedCal each event came from
        List<CalendarEvent> parentEntities = new ArrayList<>();
        for (Event event : parentsAndRegular) {
            GoogleSyncedCalendar syncedCal = resolveCalendar(event, calendars);
            parentEntities.add(googleEventMapper.toEntity(event, syncedCal));
        }
        calendarEventRepository.saveAll(parentEntities);
        calendarEventRepository.flush();

        // Save exceptions
        for (Event exception : exceptions) {
            String googleParentId = exception.getRecurringEventId();
            Optional<CalendarEvent> parentEntity = calendarEventRepository.findByGoogleEventId(googleParentId);

            if (parentEntity.isEmpty()) {
                log.warn("Orphaned exception {} — parent {} not found, skipping",
                        exception.getId(), googleParentId);
                continue;
            }

            GoogleSyncedCalendar syncedCal = resolveCalendar(exception, calendars);
            CalendarEvent exceptionEntity = googleEventMapper.toExceptionEntity(
                    exception, syncedCal, parentEntity.get());
            calendarEventRepository.save(exceptionEntity);
        }

        // Update sync metadata on all calendars
        Instant now = Instant.now();
        for (GoogleSyncedCalendar cal : calendars) {
            cal.setLastSyncedAt(now);
            syncedCalendarRepository.save(cal);
        }
    }

    /**
     * Sync a single calendar. Used when only one calendar needs syncing.
     * Deletes all Google events for the member first, then inserts from this calendar.
     */
    @Transactional
    public void fullSync(GoogleSyncedCalendar syncedCal, Calendar calendarClient) {
        try {
            List<Event> allEvents = fetchAllEvents(syncedCal, calendarClient);

            calendarEventRepository.deleteByMemberAndSource(syncedCal.getMember(), EventSource.GOOGLE);

            List<Event> parentsAndRegular = allEvents.stream()
                    .filter(e -> e.getRecurringEventId() == null)
                    .filter(e -> !"cancelled".equals(e.getStatus()))
                    .toList();

            List<Event> exceptions = allEvents.stream()
                    .filter(e -> e.getRecurringEventId() != null)
                    .toList();

            List<CalendarEvent> parentEntities = parentsAndRegular.stream()
                    .map(e -> googleEventMapper.toEntity(e, syncedCal))
                    .toList();
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

                CalendarEvent exceptionEntity = googleEventMapper.toExceptionEntity(
                        exception, syncedCal, parentEntity.get());
                calendarEventRepository.save(exceptionEntity);
            }

            syncedCal.setLastSyncedAt(Instant.now());
            syncedCalendarRepository.save(syncedCal);

        } catch (IOException e) {
            log.error("Failed to sync calendar {} for member {}: {}",
                    syncedCal.getGoogleCalendarId(), syncedCal.getMember().getId(), e.getMessage());
            throw new RuntimeException("Google Calendar sync failed", e);
        }
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

    /**
     * Google events don't carry which calendar they came from, but all calendars
     * belong to the same member, so the mapping uses the same member/family.
     * Use the first calendar as the default.
     */
    private GoogleSyncedCalendar resolveCalendar(Event event, List<GoogleSyncedCalendar> calendars) {
        // All calendars belong to the same member, so the mapping result is identical.
        // The syncedCal is only used for member/family references.
        return calendars.getFirst();
    }
}
