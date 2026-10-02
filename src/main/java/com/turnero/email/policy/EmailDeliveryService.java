package com.turnero.email.policy;

import com.turnero.business.Business;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(name = "entityManagerFactory")
public class EmailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);
    private final EmailEntitlementService entitlement;
    private final EmailDeliveryAttemptRepository attempts;
    private final Clock clock;
    private final MeterRegistry metrics;

    public EmailDeliveryService(EmailEntitlementService entitlement, EmailDeliveryAttemptRepository attempts,
            Clock clock, MeterRegistry metrics) {
        this.entitlement = entitlement; this.attempts = attempts; this.clock = clock; this.metrics = metrics;
    }

    @Transactional
    public boolean deliver(Business business, EmailType type, UUID bookingId, String idempotencyKey, BooleanSupplier providerCall) {
        var prior = attempts.findByBusinessIdAndIdempotencyKey(business.getId(), idempotencyKey);
        if (prior.isPresent()) return prior.get().getStatus() == DeliveryStatus.SENT;
        EmailEntitlementDecision decision = entitlement.decide(business.getId(), type);
        if (!decision.shouldSend()) {
            save(business, type, DeliveryStatus.OMITTED, decision.quotaSource(), bookingId, idempotencyKey, decision.reason());
            metrics.counter("turnero.email.omitted", "type", type.name(), "reason", decision.code().name()).increment();
            log.info("email omitted businessId={} bookingId={} type={} reason={}", business.getId(), bookingId, type, decision.reason());
            return false;
        }
        boolean accepted = false;
        try { accepted = providerCall.getAsBoolean(); }
        catch (RuntimeException exception) {
            log.warn("email provider failed businessId={} bookingId={} type={}", business.getId(), bookingId, type, exception);
        }
        save(business, type, accepted ? DeliveryStatus.SENT : DeliveryStatus.FAILED,
                accepted ? decision.quotaSource() : null, bookingId, idempotencyKey, accepted ? null : "PROVIDER_REJECTED");
        metrics.counter(accepted ? "turnero.email.sent" : "turnero.email.failed", "type", type.name()).increment();
        return accepted;
    }

    private void save(Business business, EmailType type, DeliveryStatus status, QuotaSource source,
            UUID bookingId, String key, String reason) {
        attempts.save(EmailDeliveryAttempt.of(business, type, status, source, bookingId, key, null,
                reason, MDC.get("requestId"), Instant.now(clock)));
    }
}
