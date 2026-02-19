package com.familyhub.demo.repository;

import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {
    List<CalendarEvent> findByFamily(Family family);
    Optional<CalendarEvent> findByFamilyAndId(Family family, UUID uuid);
}
