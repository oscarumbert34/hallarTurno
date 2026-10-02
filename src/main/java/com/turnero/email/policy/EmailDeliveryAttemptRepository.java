package com.turnero.email.policy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, UUID> {
    Optional<EmailDeliveryAttempt> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);
    long countByBusinessIdAndStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(UUID id, DeliveryStatus status, Instant from, Instant to);
    long countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(UUID id, DeliveryStatus status, QuotaSource source, Instant from, Instant to);
    long countByBusinessIdAndStatusAndOccurredAtGreaterThanEqual(UUID id, DeliveryStatus status, Instant from);

    @Modifying
    @Query("""
            delete from EmailDeliveryAttempt attempt
            where attempt.business.id = :businessId
              and attempt.idempotencyKey like concat(:prefix, '%')
            """)
    int deleteSyntheticUsage(@Param("businessId") UUID businessId, @Param("prefix") String prefix);

    @Modifying
    @Query(value = """
            insert into email_delivery_attempts (
                id, business_id, email_type, status, quota_source, idempotency_key,
                provider_id, occurred_at
            )
            select gen_random_uuid(), :businessId, :emailType, 'SENT', :quotaSource,
                   concat(:prefix, :quotaSource, ':', gen_random_uuid()),
                   'e2e-synthetic', :occurredAt
            from generate_series(1, :amount)
            """, nativeQuery = true)
    int insertSyntheticUsage(
            @Param("businessId") UUID businessId,
            @Param("emailType") String emailType,
            @Param("quotaSource") String quotaSource,
            @Param("prefix") String prefix,
            @Param("occurredAt") Instant occurredAt,
            @Param("amount") long amount
    );
}
