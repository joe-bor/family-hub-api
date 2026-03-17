package com.familyhub.demo.service;

import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.EventSource;
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

        for (GoogleSyncedCalendar cal : calendars) {
            fullSync(cal, calendarClient);
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
}
