package com.turnero.testing;

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
import com.turnero.email.policy.EmailType;
import com.turnero.email.policy.QuotaSource;
import com.turnero.security.OwnershipGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("e2e & !prod")
@Service
public class TestingEmailUsageService {
    static final String SYNTHETIC_KEY_PREFIX = "e2e-email-usage:";

    private final BusinessRepository businesses;
    private final BusinessEmailSubscriptionRepository subscriptions;
    private final EmailDeliveryAttemptRepository attempts;
    private final BusinessEmailSettingsService settings;
    private final OwnershipGuard guard;
    private final Clock clock;

    public TestingEmailUsageService(
            BusinessRepository businesses,
            BusinessEmailSubscriptionRepository subscriptions,
            EmailDeliveryAttemptRepository attempts,
            BusinessEmailSettingsService settings,
            OwnershipGuard guard,
            Clock clock
    ) {
        this.businesses = businesses;
        this.subscriptions = subscriptions;
        this.attempts = attempts;
        this.settings = settings;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional
    public EmailStatusResponse setUsage(
            UUID businessId,
            TestingEmailUsageRequest request,
            AuthenticatedUser actor
    ) {
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        guard.requireOwnerOrAdmin(
                business,
                actor,
                "Testing email usage can only be managed by the business owner or an admin"
        );
        BusinessEmailSubscription subscription = subscriptions.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email settings not found"));

        attempts.deleteSyntheticUsage(businessId, SYNTHETIC_KEY_PREFIX);
        Instant now = Instant.now(clock);
        long realAddonUsage = count(businessId, QuotaSource.ADDON, subscription);
        long realGrowthUsage = count(businessId, QuotaSource.GROWTH_INCLUDED, subscription);
        requireTargetAtLeastReal("addonUsed", request.addonUsed(), realAddonUsage);
        requireTargetAtLeastReal("growthAgendaUsed", request.growthAgendaUsed(), realGrowthUsage);

        insert(businessId, QuotaSource.ADDON, request.addonUsed() - realAddonUsage, now);
        insert(businessId, QuotaSource.GROWTH_INCLUDED,
                request.growthAgendaUsed() - realGrowthUsage, now);

        return settings.get(businessId, actor);
    }

    private long count(UUID businessId, QuotaSource source, BusinessEmailSubscription subscription) {
        return attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                businessId,
                DeliveryStatus.SENT,
                source,
                subscription.getPeriodStartedAt(),
                subscription.getPeriodEndsAt()
        );
    }

    private void requireTargetAtLeastReal(String field, int target, long realUsage) {
        if (target < realUsage) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    field + " cannot be lower than the real provider-accepted usage (" + realUsage + ")"
            );
        }
    }

    private void insert(
            UUID businessId,
            QuotaSource source,
            long amount,
            Instant occurredAt
    ) {
        if (amount <= 0) {
            return;
        }
        attempts.insertSyntheticUsage(
                businessId,
                source == QuotaSource.GROWTH_INCLUDED
                        ? EmailType.BUSINESS_DAILY_AGENDA.name()
                        : EmailType.BOOKING_CONFIRMATION.name(),
                source.name(),
                SYNTHETIC_KEY_PREFIX,
                occurredAt,
                amount
        );
    }
}
