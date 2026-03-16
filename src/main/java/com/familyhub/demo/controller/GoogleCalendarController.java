package com.familyhub.demo.controller;

import com.familyhub.demo.dto.*;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.GoogleOAuthToken;
import com.familyhub.demo.model.GoogleSyncedCalendar;
import com.familyhub.demo.repository.GoogleOAuthTokenRepository;
import com.familyhub.demo.repository.GoogleSyncedCalendarRepository;
import com.familyhub.demo.service.FamilyMemberService;
import com.familyhub.demo.service.GoogleCalendarListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/google/calendars")
@RequiredArgsConstructor
public class GoogleCalendarController {
    private final FamilyMemberService familyMemberService;
    private final GoogleCalendarListService calendarListService;
    private final GoogleSyncedCalendarRepository syncedCalendarRepository;
    private final GoogleOAuthTokenRepository tokenRepository;

    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<List<GoogleCalendarResponse>>> listCalendars(
            @PathVariable UUID memberId,
            @AuthenticationPrincipal Family family) {
        familyMemberService.findById(family, memberId);

        GoogleOAuthToken token = tokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BadRequestException("Google account not connected"));

        List<GoogleCalendarInfo> googleCalendars = calendarListService.listCalendars(memberId);

        Map<String, GoogleSyncedCalendar> storedByGoogleId = syncedCalendarRepository.findByMemberId(memberId)
                .stream()
                .collect(Collectors.toMap(GoogleSyncedCalendar::getGoogleCalendarId, Function.identity()));

        List<GoogleCalendarResponse> response = googleCalendars.stream()
                .map(cal -> {
                    GoogleSyncedCalendar stored = storedByGoogleId.get(cal.id());
                    boolean enabled = stored != null && stored.isEnabled();
                    return new GoogleCalendarResponse(cal.id(), cal.name(), cal.primary(), enabled);
                })
                .toList();

        return ResponseEntity.ok(new ApiResponse<>(response, null));
    }

    @PutMapping("/{memberId}")
    @Transactional
    public ResponseEntity<ApiResponse<List<GoogleCalendarResponse>>> updateSelection(
            @PathVariable UUID memberId,
            @RequestBody CalendarSelectionRequest request,
            @AuthenticationPrincipal Family family) {
        familyMemberService.findById(family, memberId);

        GoogleOAuthToken token = tokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BadRequestException("Google account not connected"));

        Set<String> selectedIds = new HashSet<>(request.calendarIds());

        List<GoogleCalendarInfo> googleCalendars = calendarListService.listCalendars(memberId);

        Map<String, GoogleSyncedCalendar> existingByGoogleId = syncedCalendarRepository.findByMemberId(memberId)
                .stream()
                .collect(Collectors.toMap(GoogleSyncedCalendar::getGoogleCalendarId, Function.identity()));

        List<GoogleCalendarResponse> response = new ArrayList<>();

        for (GoogleCalendarInfo cal : googleCalendars) {
            boolean shouldEnable = selectedIds.contains(cal.id());
            GoogleSyncedCalendar existing = existingByGoogleId.get(cal.id());

            if (shouldEnable) {
                if (existing == null) {
                    GoogleSyncedCalendar newCal = new GoogleSyncedCalendar();
                    newCal.setToken(token);
                    newCal.setMember(token.getMember());
                    newCal.setGoogleCalendarId(cal.id());
                    newCal.setCalendarName(cal.name());
                    newCal.setEnabled(true);
                    syncedCalendarRepository.save(newCal);
                } else {
                    existing.setEnabled(true);
                    existing.setCalendarName(cal.name());
                    syncedCalendarRepository.save(existing);
                }
            } else if (existing != null) {
                existing.setEnabled(false);
                syncedCalendarRepository.save(existing);
            }

            response.add(new GoogleCalendarResponse(cal.id(), cal.name(), cal.primary(), shouldEnable));
        }

        return ResponseEntity.ok(new ApiResponse<>(response, "Calendar selection updated"));
    }
}
