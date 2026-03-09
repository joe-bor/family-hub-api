package com.familyhub.demo.service;

import com.familyhub.demo.dto.CalendarEventResponse;
import com.familyhub.demo.mapper.CalendarEventMapper;
import com.familyhub.demo.model.CalendarEvent;
import net.fortuna.ical4j.model.Recur;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RecurrenceExpander {

    public List<CalendarEventResponse> expand(
            CalendarEvent parent,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            Map<LocalDate, CalendarEvent> exceptions
    ) {
        Recur<LocalDate> recur = new Recur<>(parent.getRecurrenceRule());
        List<LocalDate> dates = recur.getDates(parent.getDate(), rangeStart, rangeEnd);

        List<CalendarEventResponse> results = new ArrayList<>();
        for (LocalDate date : dates) {
            CalendarEvent exception = exceptions.get(date);
            if (exception != null) {
                if (exception.isCancelled()) {
                    continue; // Skip cancelled instances
                }
                // Use edited exception data
                results.add(CalendarEventMapper.toDto(exception));
            } else {
                // Virtual instance from parent
                results.add(CalendarEventMapper.toInstanceResponse(parent, date));
            }
        }
        return results;
    }
}
