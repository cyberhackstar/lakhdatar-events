package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "event_notification_jobs", indexes = {
        @Index(name = "idx_event_notification_due", columnList = "status,next_attempt_at,created_at"),
        @Index(name = "idx_event_notification_event", columnList = "event_id,order_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_notification_change", columnNames = {"event_id", "order_id", "kind", "change_key"})
})
@Getter
@Setter
public class EventNotificationJob {
    public enum Kind { CANCELLED, DETAILS_CHANGED }
    public enum Status { PENDING, PROCESSING, SENT, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Kind kind;

    @Column(name = "change_key", nullable = false, length = 128)
    private String changeKey;

    @Column(name = "old_starts_at")
    private Instant oldStartsAt;

    @Column(name = "old_ends_at")
    private Instant oldEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
