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
    @JoinColumn(name = "member_id", nullable = false,
            // Events are deleted with their member — no reassignment workflow exists.
            foreignKey = @ForeignKey(foreignKeyDefinition = "FOREIGN KEY (member_id) REFERENCES family_member(id) ON DELETE CASCADE"))
    private FamilyMember member;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    private boolean isAllDay;

    private String location;
}
