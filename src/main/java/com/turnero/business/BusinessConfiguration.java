package com.turnero.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "business_configurations")
public class BusinessConfiguration {

    @Id
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "weekly_booking_copy_enabled", nullable = false)
    private boolean weeklyBookingCopyEnabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BusinessConfiguration() {
    }

    private BusinessConfiguration(Business business, boolean weeklyBookingCopyEnabled) {
        this.business = business;
        this.weeklyBookingCopyEnabled = weeklyBookingCopyEnabled;
    }

    public static BusinessConfiguration createDefault(Business business) {
        return new BusinessConfiguration(business, false);
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public Business getBusiness() {
        return business;
    }

    public boolean isWeeklyBookingCopyEnabled() {
        return weeklyBookingCopyEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateWeeklyBookingCopyEnabled(boolean weeklyBookingCopyEnabled) {
        this.weeklyBookingCopyEnabled = weeklyBookingCopyEnabled;
    }
}
