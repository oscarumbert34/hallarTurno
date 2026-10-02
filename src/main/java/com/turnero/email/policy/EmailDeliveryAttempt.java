package com.turnero.email.policy;

import com.turnero.business.Business;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_delivery_attempts")
public class EmailDeliveryAttempt {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "business_id") private Business business;
    @Enumerated(EnumType.STRING) @Column(name = "email_type", nullable = false) private EmailType emailType;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "quota_source") private QuotaSource quotaSource;
    @Column(name = "booking_id") private UUID bookingId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "provider_id") private String providerId;
    @Column(name = "omission_reason") private String omissionReason;
    @Column(name = "request_id") private String requestId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected EmailDeliveryAttempt() {}

    public static EmailDeliveryAttempt of(Business business, EmailType type, DeliveryStatus status,
            QuotaSource source, UUID bookingId, String key, String providerId, String reason,
            String requestId, Instant occurredAt) {
        EmailDeliveryAttempt a = new EmailDeliveryAttempt(); a.id = UUID.randomUUID(); a.business = business;
        a.emailType = type; a.status = status; a.quotaSource = source; a.bookingId = bookingId;
        a.idempotencyKey = key; a.providerId = providerId; a.omissionReason = reason;
        a.requestId = requestId; a.occurredAt = occurredAt; return a;
    }
    public DeliveryStatus getStatus() { return status; }
    public EmailType getEmailType() { return emailType; }
    public QuotaSource getQuotaSource() { return quotaSource; }
    public Instant getOccurredAt() { return occurredAt; }
}
