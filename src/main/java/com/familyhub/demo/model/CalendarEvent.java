package com.familyhub.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Getter
@Setter
public class CalendarEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private FamilyMember member;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    private LocalDate endDate;

    private boolean isAllDay;

    private String location;

    private String recurrenceRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_event_id")
    private CalendarEvent recurringEvent;

    private LocalDate originalDate;

    @Column(nullable = false)
    private boolean isCancelled;
}
