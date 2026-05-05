package com.familyhub.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
public class Chore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne
    @JoinColumn(name = "assigned_to_member_id", nullable = false)
    private FamilyMember assignedToMember;

    @Column(nullable = false, length = 100)
    private String title;

    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean completed;

    private LocalDateTime completedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
