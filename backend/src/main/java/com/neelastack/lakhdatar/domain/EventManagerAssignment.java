package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "event_manager_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_manager_assignment", columnNames = {"event_id", "user_id"}))
@Getter
@Setter
public class EventManagerAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
