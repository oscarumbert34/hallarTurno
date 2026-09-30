package com.turnero.email.policy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, UUID> {
    Optional<EmailDeliveryAttempt> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);
    long countByBusinessIdAndStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(UUID id, DeliveryStatus status, Instant from, Instant to);
    long countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(UUID id, DeliveryStatus status, QuotaSource source, Instant from, Instant to);
    long countByBusinessIdAndStatusAndOccurredAtGreaterThanEqual(UUID id, DeliveryStatus status, Instant from);
}
