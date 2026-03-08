package com.familyhub.demo.service;

import com.familyhub.demo.dto.CalendarEventRequest;
import com.familyhub.demo.dto.CalendarEventResponse;
import com.familyhub.demo.exception.BadRequestException;
import com.familyhub.demo.exception.ResourceNotFoundException;
import com.familyhub.demo.mapper.CalendarEventMapper;
import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.repository.CalendarEventRepository;
import com.familyhub.demo.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {
    private final CalendarEventRepository calendarEventRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public List<CalendarEventResponse> getAllEventsByFamily(
            Family family,
            LocalDate startDate,
            LocalDate endDate,
            UUID memberId
    ) {
        Stream<CalendarEvent> calendarEventStream = calendarEventRepository.findByFamily(family).stream();

        Predicate<CalendarEvent> byMemberId = event -> event.getMember().getId().equals(memberId);

        if (memberId != null) {
            // Validate memberId passed belongs to logged in Family
            FamilyMember familyMember = familyMemberRepository.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Family Member", memberId));
            if (!familyMember.getFamily().getId().equals(family.getId())) {
                throw new AccessDeniedException("Access Denied -- CalendarEventService.getAllEventsByFamily()");
            }

            calendarEventStream = calendarEventStream.filter(byMemberId);
        }

        if (startDate != null && endDate != null) {
            // Range-overlap: event.date <= rangeEnd AND coalesce(event.endDate, event.date) >= rangeStart
            LocalDate rangeStart = startDate;
            LocalDate rangeEnd = endDate;
            Predicate<CalendarEvent> byDateRange = event -> {
                LocalDate eventStart = event.getDate();
                LocalDate eventEnd = event.getEndDate() != null ? event.getEndDate() : event.getDate();
                return !eventStart.isAfter(rangeEnd) && !eventEnd.isBefore(rangeStart);
            };
            calendarEventStream = calendarEventStream.filter(byDateRange);
        } else if (startDate != null) {
            calendarEventStream = calendarEventStream.filter(event -> !event.getDate().isBefore(startDate));
        } else if (endDate != null) {
            calendarEventStream = calendarEventStream.filter(event -> !event.getDate().isAfter(endDate));
        }

        return calendarEventStream
                .map(CalendarEventMapper::toDto)
                .toList();
    }

    public CalendarEventResponse getEventById(UUID id, Family family) {
        CalendarEvent calendarEvent = calendarEventRepository.findByFamilyAndId(family, id)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar Event", id));

        return CalendarEventMapper.toDto(calendarEvent);
    }

    @Transactional
    public CalendarEventResponse addCalendarEvent(CalendarEventRequest request, Family family) {
        // Validate memberId passed belongs to logged in Family
        FamilyMember familyMember = familyMemberRepository.findById(request.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Family Member", request.memberId()));
        if (!familyMember.getFamily().getId().equals(family.getId())) {
            throw new AccessDeniedException("Access Denied -- CalendarEventService.addCalendarEvent()");
        }

        // We turn DTO to an Entity so we can save it in our DB
        CalendarEvent calendarEvent = CalendarEventMapper.toEntity(request, family, familyMember);
        isEventTimeRangeValid(calendarEvent);
        validateEndDate(calendarEvent);

        CalendarEvent saved = calendarEventRepository.save(calendarEvent);

        return CalendarEventMapper.toDto(saved);

    }

    @Transactional
    public CalendarEventResponse updateCalendarEvent(CalendarEventRequest request, UUID eventId, Family family) {
        // Validate memberId passed belongs to logged in Family
        FamilyMember familyMember = familyMemberRepository.findById(request.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Family Member", request.memberId()));
        if (!familyMember.getFamily().getId().equals(family.getId())) {
            throw new AccessDeniedException("Access Denied -- CalendarEventService.updateCalendarEvent()");
        }

        // Validate the resource belongs to current family
        CalendarEvent calendarEvent = calendarEventRepository.findByFamilyAndId(family, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar Event", eventId));

        // Map DTO to Entity to handle `string <-> date/time` conversions
        CalendarEvent update = CalendarEventMapper.toEntity(request, family, familyMember);
        isEventTimeRangeValid(update);
        validateEndDate(update);

        // Apply changes
        calendarEvent.setTitle(update.getTitle());
        calendarEvent.setStartTime(update.getStartTime());
        calendarEvent.setEndTime(update.getEndTime());
        calendarEvent.setDate(update.getDate());
        calendarEvent.setAllDay(update.isAllDay());
        calendarEvent.setLocation(update.getLocation());
        calendarEvent.setEndDate(update.getEndDate());
        calendarEvent.setMember(update.getMember());

        CalendarEvent saved = calendarEventRepository.save(calendarEvent);

        return CalendarEventMapper.toDto(saved);
    }

    @Transactional
    public void deleteCalendarEvent(UUID id, Family family) {
        CalendarEvent calendarEvent = calendarEventRepository.findByFamilyAndId(family, id)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar Event", id));

        calendarEventRepository.delete(calendarEvent);
    }

    private void isEventTimeRangeValid(CalendarEvent event) {
        //  an "all day event" should not be constrained by this check. (eg. Birthday 12 AM - 12 AM)
        if (!event.isAllDay() && event.getStartTime().isAfter(event.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }
    }

    private void validateEndDate(CalendarEvent event) {
        if (event.getEndDate() == null) {
            return;
        }
        if (!event.isAllDay()) {
            throw new BadRequestException("End date is only valid for all-day events");
        }
        if (event.getEndDate().isBefore(event.getDate())) {
            throw new BadRequestException("End date must be on or after start date");
        }
        // Normalize: endDate == date means single-day, store as null
        if (event.getEndDate().equals(event.getDate())) {
            event.setEndDate(null);
        }
    }
}
