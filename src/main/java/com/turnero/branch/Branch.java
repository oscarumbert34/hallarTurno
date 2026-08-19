package com.turnero.branch;

import com.turnero.business.Business;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "branches",
        indexes = {
                @Index(name = "idx_branches_business_id", columnList = "business_id"),
                @Index(name = "idx_branches_status", columnList = "status")
        }
)
public class Branch {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @NotBlank
    @Column(nullable = false, length = 160)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 240)
    private String address;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String locality;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String province;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String country;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BranchStatus status;

    @NotBlank
    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BranchOpeningInterval> openingIntervals = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Branch() {
    }

    private Branch(
            Business business,
            String name,
            String address,
            String locality,
            String province,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            BranchStatus status,
            String zoneId
    ) {
        this.business = business;
        this.name = name;
        this.address = address;
        this.locality = locality;
        this.province = province;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.zoneId = zoneId;
    }

    public static Branch create(
            Business business,
            String name,
            String address,
            String locality,
            String province,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            BranchStatus status,
            String zoneId
    ) {
        return new Branch(business, name, address, locality, province, country, latitude, longitude, status, zoneId);
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

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getLocality() {
        return locality;
    }

    public String getProvince() {
        return province;
    }

    public String getCountry() {
        return country;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BranchStatus getStatus() {
        return status;
    }

    public String getZoneId() {
        return zoneId;
    }

    public List<BranchOpeningInterval> getOpeningIntervals() {
        return openingIntervals.stream()
                .sorted(Comparator.comparing(BranchOpeningInterval::getDayOfWeek)
                        .thenComparing(BranchOpeningInterval::getOpensAt))
                .toList();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            String name,
            String address,
            String locality,
            String province,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            BranchStatus status,
            String zoneId
    ) {
        this.name = name;
        this.address = address;
        this.locality = locality;
        this.province = province;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.zoneId = zoneId;
    }

    public void replaceOpeningIntervals(List<OpeningIntervalValue> intervals) {
        openingIntervals.clear();
        intervals.forEach(interval -> openingIntervals.add(new BranchOpeningInterval(
                this,
                interval.dayOfWeek(),
                interval.opensAt(),
                interval.closesAt()
        )));
    }

    record OpeningIntervalValue(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
    }
}
