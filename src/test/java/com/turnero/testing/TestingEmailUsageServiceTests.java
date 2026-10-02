package com.turnero.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.email.policy.BusinessEmailSettingsService;
import com.turnero.email.policy.BusinessEmailSubscription;
import com.turnero.email.policy.BusinessEmailSubscriptionRepository;
import com.turnero.email.policy.DeliveryStatus;
import com.turnero.email.policy.EmailDeliveryAttemptRepository;
import com.turnero.email.policy.EmailStatusResponse;
import com.turnero.email.policy.QuotaSource;
import com.turnero.security.OwnershipGuard;
import com.turnero.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestingEmailUsageServiceTests {
    private final UUID businessId = UUID.randomUUID();
    private final AuthenticatedUser actor = new AuthenticatedUser(
            UUID.randomUUID(),
            "owner@example.com",
            Set.of(UserRole.BUSINESS)
    );
    private final Instant now = Instant.parse("2026-10-01T12:00:00Z");
    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final BusinessEmailSubscriptionRepository subscriptions =
            mock(BusinessEmailSubscriptionRepository.class);
    private final EmailDeliveryAttemptRepository attempts = mock(EmailDeliveryAttemptRepository.class);
    private final BusinessEmailSettingsService settings = mock(BusinessEmailSettingsService.class);
    private final OwnershipGuard guard = mock(OwnershipGuard.class);
    private final Business business = mock(Business.class);
    private final BusinessEmailSubscription subscription = mock(BusinessEmailSubscription.class);
    private TestingEmailUsageService service;

    @BeforeEach
    void setUp() {
        service = new TestingEmailUsageService(
                businesses,
                subscriptions,
                attempts,
                settings,
                guard,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(subscriptions.findByIdForUpdate(businessId)).thenReturn(Optional.of(subscription));
        when(subscription.getPeriodStartedAt()).thenReturn(now.minusSeconds(60));
        when(subscription.getPeriodEndsAt()).thenReturn(now.plusSeconds(60));
    }

    @Test
    void createsOnlyTheSyntheticDeltaNeededToReachTheRequestedUsage() {
        EmailStatusResponse expected = mock(EmailStatusResponse.class);
        when(attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                eq(businessId), eq(DeliveryStatus.SENT), eq(QuotaSource.ADDON), any(), any()
        )).thenReturn(2L);
        when(attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                eq(businessId), eq(DeliveryStatus.SENT), eq(QuotaSource.GROWTH_INCLUDED), any(), any()
        )).thenReturn(1L);
        when(settings.get(businessId, actor)).thenReturn(expected);

        EmailStatusResponse result = service.setUsage(
                businessId,
                new TestingEmailUsageRequest(5, 3),
                actor
        );

        verify(attempts).deleteSyntheticUsage(businessId, TestingEmailUsageService.SYNTHETIC_KEY_PREFIX);
        verify(attempts).insertSyntheticUsage(
                businessId,
                "BOOKING_CONFIRMATION",
                "ADDON",
                TestingEmailUsageService.SYNTHETIC_KEY_PREFIX,
                now,
                3
        );
        verify(attempts).insertSyntheticUsage(
                businessId,
                "BUSINESS_DAILY_AGENDA",
                "GROWTH_INCLUDED",
                TestingEmailUsageService.SYNTHETIC_KEY_PREFIX,
                now,
                2
        );
        assertThat(result).isSameAs(expected);
    }

    @Test
    void refusesToHideRealProviderAcceptedUsage() {
        when(attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                eq(businessId), eq(DeliveryStatus.SENT), eq(QuotaSource.ADDON), any(), any()
        )).thenReturn(4L);
        when(attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                eq(businessId), eq(DeliveryStatus.SENT), eq(QuotaSource.GROWTH_INCLUDED), any(), any()
        )).thenReturn(0L);

        assertThatThrownBy(() -> service.setUsage(
                businessId,
                new TestingEmailUsageRequest(3, 0),
                actor
        ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be lower than the real provider-accepted usage");
    }
}
