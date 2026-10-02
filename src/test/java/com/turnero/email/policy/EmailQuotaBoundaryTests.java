package com.turnero.email.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.business.Business;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailQuotaBoundaryTests {
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void essentialAllowsTheFourHundredthEmail() {
        Fixture fixture = fixtureWithUsedEmails(399);

        EmailEntitlementDecision decision = fixture.entitlement().decide(
                fixture.businessId(),
                EmailType.BOOKING_CONFIRMATION
        );

        assertThat(decision.code()).isEqualTo(EmailDecisionCode.SEND);
        assertThat(decision.used()).isEqualTo(399);
        assertThat(decision.limit()).isEqualTo(400);
    }

    @Test
    void essentialBlocksTheFourHundredAndFirstEmailWithoutCallingTheProvider() {
        Fixture fixture = fixtureWithUsedEmails(400);
        EmailEntitlementDecision decision = fixture.entitlement().decide(
                fixture.businessId(),
                EmailType.BOOKING_CONFIRMATION
        );
        assertThat(decision.code()).isEqualTo(EmailDecisionCode.QUOTA_EXCEEDED);
        assertThat(decision.used()).isEqualTo(400);
        assertThat(decision.limit()).isEqualTo(400);

        EmailEntitlementService entitlement = mock(EmailEntitlementService.class);
        EmailDeliveryAttemptRepository attempts = mock(EmailDeliveryAttemptRepository.class);
        EmailDeliveryService delivery = new EmailDeliveryService(
                entitlement,
                attempts,
                CLOCK,
                new SimpleMeterRegistry()
        );
        Business business = mock(Business.class);
        UUID bookingId = UUID.randomUUID();
        AtomicBoolean providerCalled = new AtomicBoolean(false);
        when(business.getId()).thenReturn(fixture.businessId());
        when(attempts.findByBusinessIdAndIdempotencyKey(fixture.businessId(), "booking-401"))
                .thenReturn(Optional.empty());
        when(entitlement.decide(fixture.businessId(), EmailType.BOOKING_CONFIRMATION))
                .thenReturn(decision);

        boolean sent = delivery.deliver(
                business,
                EmailType.BOOKING_CONFIRMATION,
                bookingId,
                "booking-401",
                () -> {
                    providerCalled.set(true);
                    return true;
                }
        );

        assertThat(sent).isFalse();
        assertThat(providerCalled).isFalse();
        ArgumentCaptor<EmailDeliveryAttempt> saved = ArgumentCaptor.forClass(EmailDeliveryAttempt.class);
        verify(attempts).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.OMITTED);
        assertThat(saved.getValue().getQuotaSource()).isEqualTo(QuotaSource.ADDON);
        verify(attempts, never()).saveAll(any());
    }

    private Fixture fixtureWithUsedEmails(long used) {
        UUID businessId = UUID.randomUUID();
        BusinessEmailSubscriptionRepository subscriptions = mock(BusinessEmailSubscriptionRepository.class);
        EmailDeliveryAttemptRepository attempts = mock(EmailDeliveryAttemptRepository.class);
        BusinessEmailSubscription subscription = mock(BusinessEmailSubscription.class);
        EmailCapabilityCatalog catalog = new EmailCapabilityCatalog();
        when(subscriptions.findByIdForUpdate(businessId)).thenReturn(Optional.of(subscription));
        when(subscription.preferenceEnabled(EmailType.BOOKING_CONFIRMATION)).thenReturn(true);
        when(subscription.effectiveAddon(NOW)).thenReturn(EmailAddon.ESSENTIAL);
        when(subscription.getPeriodStartedAt()).thenReturn(NOW.minusSeconds(60));
        when(subscription.getPeriodEndsAt()).thenReturn(NOW.plusSeconds(60));
        when(attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                eq(businessId),
                eq(DeliveryStatus.SENT),
                eq(QuotaSource.ADDON),
                any(),
                any()
        )).thenReturn(used);
        return new Fixture(
                businessId,
                new EmailEntitlementService(subscriptions, attempts, catalog, CLOCK)
        );
    }

    private record Fixture(UUID businessId, EmailEntitlementService entitlement) {
    }
}
