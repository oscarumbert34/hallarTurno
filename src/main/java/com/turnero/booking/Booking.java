package com.turnero.booking;

import com.turnero.branch.Branch;
import com.turnero.business.Business;
import com.turnero.employee.BookableResource;
import com.turnero.service.ServiceOffering;
import com.turnero.user.User;
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
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_resource_time", columnList = "resource_id,starts_at,ends_at"),
                @Index(name = "idx_bookings_branch_time", columnList = "branch_id,starts_at,ends_at"),
                @Index(name = "idx_bookings_status", columnList = "status")
        }
)
public class Booking {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_offering_id", nullable = false)
    private ServiceOffering serviceOffering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private BookableResource resource;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "service_name_snapshot", nullable = false, length = 160)
    private String serviceNameSnapshot;

    @Column(name = "resource_name_snapshot", nullable = false, length = 160)
    private String resourceNameSnapshot;

    @Column(name = "customer_name_snapshot", nullable = false, length = 120)
    private String customerNameSnapshot;

    @Column(name = "customer_phone_snapshot", nullable = false, length = 40)
    private String customerPhoneSnapshot;

    @Column(name = "duration_minutes_snapshot", nullable = false)
    private Integer durationMinutesSnapshot;

    @Column(name = "price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "currency_snapshot", nullable = false, length = 3)
    private String currencySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
    }

    private Booking(
            Branch branch,
            Business business,
            User customer,
            ServiceOffering serviceOffering,
            BookableResource resource,
            Instant startsAt,
            Instant endsAt,
            String serviceNameSnapshot,
            String resourceNameSnapshot,
            String customerNameSnapshot,
            String customerPhoneSnapshot,
            Integer durationMinutesSnapshot,
            BigDecimal priceSnapshot,
            String currencySnapshot,
            BookingStatus status
    ) {
        this.branch = branch;
        this.business = business;
        this.customer = customer;
        this.serviceOffering = serviceOffering;
        this.resource = resource;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.serviceNameSnapshot = serviceNameSnapshot;
        this.resourceNameSnapshot = resourceNameSnapshot;
        this.customerNameSnapshot = customerNameSnapshot;
        this.customerPhoneSnapshot = customerPhoneSnapshot;
        this.durationMinutesSnapshot = durationMinutesSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.currencySnapshot = currencySnapshot;
        this.status = status;
    }

    public static Booking create(
            Branch branch,
            Business business,
            User customer,
            ServiceOffering serviceOffering,
            BookableResource resource,
            Instant startsAt,
            Instant endsAt,
            String serviceNameSnapshot,
            String resourceNameSnapshot,
            String customerNameSnapshot,
            String customerPhoneSnapshot,
            Integer durationMinutesSnapshot,
            BigDecimal priceSnapshot,
            String currencySnapshot,
            BookingStatus status
    ) {
        return new Booking(
                branch,
                business,
                customer,
                serviceOffering,
                resource,
                startsAt,
                endsAt,
                serviceNameSnapshot,
                resourceNameSnapshot,
                customerNameSnapshot,
                customerPhoneSnapshot,
                durationMinutesSnapshot,
                priceSnapshot,
                currencySnapshot,
                status
        );
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

    public Business getBusiness() {
        return business;
    }

    public User getCustomer() {
        return customer;
    }

    public ServiceOffering getServiceOffering() {
        return serviceOffering;
    }

    public BookableResource getResource() {
        return resource;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getServiceNameSnapshot() {
        return serviceNameSnapshot;
    }

    public String getResourceNameSnapshot() {
        return resourceNameSnapshot;
    }

    public String getCustomerNameSnapshot() {
        return customerNameSnapshot;
    }

    public String getCustomerPhoneSnapshot() {
        return customerPhoneSnapshot;
    }

    public Integer getDurationMinutesSnapshot() {
        return durationMinutesSnapshot;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public String getCurrencySnapshot() {
        return currencySnapshot;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public User getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void cancel(User cancelledBy, Instant cancelledAt) {
        if (status == BookingStatus.CANCELLED) {
            return;
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
    }
}


