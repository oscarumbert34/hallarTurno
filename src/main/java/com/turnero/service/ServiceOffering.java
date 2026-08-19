package com.turnero.service;

import com.turnero.branch.Branch;
import com.turnero.business.Business;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "service_offerings",
        indexes = {
                @Index(name = "idx_service_offerings_business_id", columnList = "business_id"),
                @Index(name = "idx_service_offerings_branch_id", columnList = "branch_id"),
                @Index(name = "idx_service_offerings_status", columnList = "status")
        }
)
public class ServiceOffering {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @NotBlank
    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 500)
    private String description;

    @NotNull
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotBlank
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ServiceOfferingStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceOffering() {
    }

    private ServiceOffering(
            Business business,
            Branch branch,
            String name,
            String description,
            Integer durationMinutes,
            BigDecimal price,
            String currency,
            ServiceOfferingStatus status
    ) {
        this.business = business;
        this.branch = branch;
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.currency = currency;
        this.status = status;
    }

    public static ServiceOffering create(
            Business business,
            Branch branch,
            String name,
            String description,
            Integer durationMinutes,
            BigDecimal price,
            String currency,
            ServiceOfferingStatus status
    ) {
        return new ServiceOffering(business, branch, name, description, durationMinutes, price, currency, status);
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

    public Business getBusiness() {
        return business;
    }

    public Branch getBranch() {
        return branch;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public ServiceOfferingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            Branch branch,
            String name,
            String description,
            Integer durationMinutes,
            BigDecimal price,
            String currency,
            ServiceOfferingStatus status
    ) {
        this.branch = branch;
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.currency = currency;
        this.status = status;
    }

    public void deactivate() {
        this.status = ServiceOfferingStatus.INACTIVE;
    }
}
