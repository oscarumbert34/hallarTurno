package com.turnero.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "branch_schedule_exceptions", uniqueConstraints =
        @UniqueConstraint(name = "uk_branch_schedule_exception_date", columnNames = {"branch_id", "exception_date"}))
public class BranchScheduleException {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "exception_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BranchScheduleExceptionType type;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BranchScheduleException() {
    }

    private BranchScheduleException(Branch branch, LocalDate date, BranchScheduleExceptionType type,
                                    LocalTime startTime, LocalTime endTime, String reason) {
        this.branch = branch;
        update(date, type, startTime, endTime, reason);
    }

    public static BranchScheduleException create(Branch branch, LocalDate date, BranchScheduleExceptionType type,
                                                 LocalTime startTime, LocalTime endTime, String reason) {
        return new BranchScheduleException(branch, date, type, startTime, endTime, reason);
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public void update(LocalDate date, BranchScheduleExceptionType type, LocalTime startTime,
                       LocalTime endTime, String reason) {
        this.date = date;
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public UUID getId() { return id; }
    public Branch getBranch() { return branch; }
    public LocalDate getDate() { return date; }
    public BranchScheduleExceptionType getType() { return type; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
