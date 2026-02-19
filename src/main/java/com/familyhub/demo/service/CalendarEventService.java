package com.familyhub.demo.service;

import com.familyhub.demo.dto.CalendarEventRequest;
import com.familyhub.demo.dto.CalendarEventResponse;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarEventService {
    private final CalendarEventRepository calendarEventRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public List<CalendarEventResponse> getAllEventsByFamily(Family family) {
        List<CalendarEvent> eventsByFamily = calendarEventRepository.findByFamily(family);

        return eventsByFamily.stream()
                .map(CalendarEventMapper::toDto)
                .toList();
    }

    public CalendarEventResponse getEventById(UUID id, Family family) {
        CalendarEvent calendarEvent = calendarEventRepository.findByFamilyAndId(family, id)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar Event", id));

        return CalendarEventMapper.toDto(calendarEvent);
    }


    public CalendarEventResponse addCalendarEvent(CalendarEventRequest request, Family family) {
        // Validate memberId passed belongs to logged in Family
        FamilyMember familyMember = familyMemberRepository.findById(request.memberId())
                .orElseThrow(() -> new ResourceNotFoundException("Family Member", request.memberId()));
        if (!familyMember.getFamily().getId().equals(family.getId())) {
            throw new AccessDeniedException("Access Denied -- CalendarEventService.addCalendarEvent()");
        }

        // We turn DTO to an Entity so we can save it in our DB
        CalendarEvent calendarEvent = CalendarEventMapper.toEntity(request, family);

        calendarEvent.setMember(familyMember);

        CalendarEvent saved = calendarEventRepository.save(calendarEvent);

        return CalendarEventMapper.toDto(saved);

    }


}
