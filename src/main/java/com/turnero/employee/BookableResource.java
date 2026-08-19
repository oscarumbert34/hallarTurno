package com.turnero.employee;

import com.turnero.branch.Branch;
import com.turnero.service.ServiceOffering;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "bookable_resources",
        indexes = {
                @Index(name = "idx_bookable_resources_branch_id", columnList = "branch_id"),
                @Index(name = "idx_bookable_resources_status", columnList = "status")
        }
)
public class BookableResource {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @NotBlank
    @Column(name = "visible_name", nullable = false, length = 160)
    private String visibleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookableResourceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookableResourceStatus status;

    @ManyToMany
    @JoinTable(
            name = "bookable_resource_service_offerings",
            joinColumns = @JoinColumn(name = "resource_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "service_offering_id", nullable = false)
    )
    private Set<ServiceOffering> serviceOfferings = new LinkedHashSet<>();

    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ResourceWorkingInterval> workingIntervals = new LinkedHashSet<>();

    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ResourceAbsence> absences = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookableResource() {
    }

    private BookableResource(
            Branch branch,
            String visibleName,
            BookableResourceType type,
            BookableResourceStatus status
    ) {
        this.branch = branch;
        this.visibleName = visibleName;
        this.type = type;
        this.status = status;
    }

    public static BookableResource create(
            Branch branch,
            String visibleName,
            BookableResourceType type,
            BookableResourceStatus status
    ) {
        return new BookableResource(branch, visibleName, type, status);
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public Branch getBranch() {
        return branch;
    }

    public String getVisibleName() {
        return visibleName;
    }

    public BookableResourceType getType() {
        return type;
    }

    public BookableResourceStatus getStatus() {
        return status;
    }

    public Set<ServiceOffering> getServiceOfferings() {
        return Set.copyOf(serviceOfferings);
    }

    public List<ResourceWorkingInterval> getWorkingIntervals() {
        return workingIntervals.stream()
                .sorted(Comparator.comparing(ResourceWorkingInterval::getDayOfWeek)
                        .thenComparing(ResourceWorkingInterval::getStartsAt))
                .toList();
    }

    public List<ResourceAbsence> getAbsences() {
        return absences.stream()
                .sorted(Comparator.comparing(ResourceAbsence::getDate)
                        .thenComparing(ResourceAbsence::getStartsAt))
                .toList();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            String visibleName,
            BookableResourceType type,
            BookableResourceStatus status,
            Set<ServiceOffering> serviceOfferings
    ) {
        this.visibleName = visibleName;
        this.type = type;
        this.status = status;
        replaceServiceOfferings(serviceOfferings);
    }

    public void replaceServiceOfferings(Set<ServiceOffering> serviceOfferings) {
        this.serviceOfferings.clear();
        this.serviceOfferings.addAll(serviceOfferings);
    }

    public void replaceWorkingIntervals(List<WorkingIntervalValue> intervals) {
        workingIntervals.clear();
        intervals.forEach(interval -> workingIntervals.add(new ResourceWorkingInterval(
                this,
                interval.dayOfWeek(),
                interval.startsAt(),
                interval.endsAt()
        )));
    }

    public void replaceAbsences(List<AbsenceValue> values) {
        absences.clear();
        values.forEach(value -> absences.add(new ResourceAbsence(
                this,
                value.date(),
                value.startsAt(),
                value.endsAt()
        )));
    }

    public void deactivate() {
        status = BookableResourceStatus.INACTIVE;
    }

    record WorkingIntervalValue(DayOfWeek dayOfWeek, LocalTime startsAt, LocalTime endsAt) {
    }

    record AbsenceValue(LocalDate date, LocalTime startsAt, LocalTime endsAt) {
    }
}
