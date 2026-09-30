package com.turnero.email.policy;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@Service
@ConditionalOnBean(name = "entityManagerFactory")
public class EmailEntitlementService {
    public static final int GROWTH_AGENDA_LIMIT = 30;
    private final BusinessEmailSubscriptionRepository subscriptions;
    private final EmailDeliveryAttemptRepository attempts;
    private final EmailCapabilityCatalog catalog;
    private final Clock clock;

    public EmailEntitlementService(BusinessEmailSubscriptionRepository subscriptions,
            EmailDeliveryAttemptRepository attempts, EmailCapabilityCatalog catalog, Clock clock) {
        this.subscriptions = subscriptions; this.attempts = attempts; this.catalog = catalog; this.clock = clock;
    }

    public EmailEntitlementDecision decide(UUID businessId, EmailType type) {
        Instant now = Instant.now(clock);
        BusinessEmailSubscription s = subscriptions.findByIdForUpdate(businessId)
                .orElseThrow(() -> new IllegalStateException("Email subscription not found for business " + businessId));
        s.expireTrial(now); s.applyPendingAddon(now);
        if (!s.preferenceEnabled(type)) return omitted(EmailDecisionCode.DISABLED, "PREFERENCE_DISABLED");

        if (type == EmailType.BUSINESS_DAILY_AGENDA && s.getBasePlan() == BasePlan.GROWTH) {
            int used = Math.toIntExact(attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                    businessId, DeliveryStatus.SENT, QuotaSource.GROWTH_INCLUDED, s.getPeriodStartedAt(), s.getPeriodEndsAt()));
            if (used < GROWTH_AGENDA_LIMIT) return new EmailEntitlementDecision(EmailDecisionCode.SEND, QuotaSource.GROWTH_INCLUDED, used, GROWTH_AGENDA_LIMIT, null);
        }

        EmailAddon addon = s.effectiveAddon(now);
        if (!catalog.includes(addon, type)) return omitted(EmailDecisionCode.NOT_INCLUDED, "EMAIL_FEATURE_REQUIRED");
        int used = Math.toIntExact(attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                businessId, DeliveryStatus.SENT, QuotaSource.ADDON, s.getPeriodStartedAt(), s.getPeriodEndsAt()));
        int limit = addon.monthlyLimit();
        if (used >= limit) return new EmailEntitlementDecision(EmailDecisionCode.QUOTA_EXCEEDED, QuotaSource.ADDON, used, limit, "EMAIL_QUOTA_EXCEEDED");
        return new EmailEntitlementDecision(EmailDecisionCode.SEND, QuotaSource.ADDON, used, limit, null);
    }

    private EmailEntitlementDecision omitted(EmailDecisionCode code, String reason) {
        return new EmailEntitlementDecision(code, null, 0, 0, reason);
    }
}
