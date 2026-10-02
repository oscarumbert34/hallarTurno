package com.turnero.email.policy;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessEmailSettingsService {
    private static final Logger log = LoggerFactory.getLogger(BusinessEmailSettingsService.class);
    private final BusinessRepository businesses;
    private final BusinessEmailSubscriptionRepository subscriptions;
    private final EmailDeliveryAttemptRepository attempts;
    private final EmailCapabilityCatalog catalog;
    private final OwnershipGuard guard;
    private final Clock clock;

    public BusinessEmailSettingsService(BusinessRepository businesses, BusinessEmailSubscriptionRepository subscriptions,
            EmailDeliveryAttemptRepository attempts, EmailCapabilityCatalog catalog, OwnershipGuard guard, Clock clock) {
        this.businesses = businesses; this.subscriptions = subscriptions; this.attempts = attempts;
        this.catalog = catalog; this.guard = guard; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EmailStatusResponse get(UUID businessId, AuthenticatedUser actor) {
        Business business = owned(businessId, actor);
        BusinessEmailSubscription s = subscriptions.findById(business.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email settings not found"));
        return response(s);
    }

    @Transactional
    public EmailStatusResponse updatePreferences(UUID businessId, EmailPreferencesRequest request, AuthenticatedUser actor) {
        owned(businessId, actor);
        BusinessEmailSubscription s = locked(businessId);
        s.updatePreferences(request.confirmationEnabled(), request.dailyAgendaEnabled(),
                request.reminderActionEnabled(), request.cancellationEnabled());
        log.info("email preferences changed businessId={} actorId={}", businessId, actor.id());
        return response(s);
    }

    @Transactional
    public EmailStatusResponse selectAddon(UUID businessId, EmailAddonRequest request, AuthenticatedUser actor) {
        owned(businessId, actor);
        BusinessEmailSubscription s = locked(businessId);
        s.selectAddon(request.addon(), Instant.now(clock));
        log.info("email addon changed businessId={} actorId={} selected={} reason={}",
                businessId, actor.id(), request.addon(), request.reason());
        return response(s);
    }

    private Business owned(UUID id, AuthenticatedUser actor) {
        Business business = businesses.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        guard.requireOwnerOrAdmin(business, actor, "Email settings can only be managed by the business owner or an admin");
        return business;
    }

    private BusinessEmailSubscription locked(UUID id) {
        return subscriptions.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email settings not found"));
    }

    private EmailStatusResponse response(BusinessEmailSubscription s) {
        Instant now = Instant.now(clock); EmailAddon effective = s.effectiveAddon(now);
        long used = attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                s.getBusinessId(), DeliveryStatus.SENT, QuotaSource.ADDON, s.getPeriodStartedAt(), s.getPeriodEndsAt());
        long growthUsed = attempts.countByBusinessIdAndStatusAndQuotaSourceAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                s.getBusinessId(), DeliveryStatus.SENT, QuotaSource.GROWTH_INCLUDED, s.getPeriodStartedAt(), s.getPeriodEndsAt());
        var automations = Arrays.stream(EmailType.values()).map(type -> new EmailAutomationState(type,
                catalog.includes(effective, type) || (type == EmailType.BUSINESS_DAILY_AGENDA && s.getBasePlan() == BasePlan.GROWTH),
                s.preferenceEnabled(type))).toList();
        int limit = effective.monthlyLimit();
        long failures = attempts.countByBusinessIdAndStatusAndOccurredAtGreaterThanEqual(
                s.getBusinessId(), DeliveryStatus.FAILED, now.minus(7, ChronoUnit.DAYS));
        return new EmailStatusResponse(s.getBasePlan(), effective, s.getPendingAddon(), s.getStatus(),
                s.getPeriodStartedAt(), s.getPeriodEndsAt(), s.getTrialEndsAt(), automations,
                new EmailUsage(used, limit, Math.max(0, limit - used), growthUsed,
                        s.getBasePlan() == BasePlan.GROWTH ? EmailEntitlementService.GROWTH_AGENDA_LIMIT : 0), failures);
    }
}
