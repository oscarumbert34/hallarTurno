package com.turnero.email.policy;

import java.time.Instant;
import java.util.List;

public record EmailStatusResponse(BasePlan basePlan, EmailAddon addon, EmailAddon pendingAddon,
        SubscriptionStatus status, Instant periodStartedAt, Instant periodEndsAt,
        Instant trialEndsAt, List<EmailAutomationState> automations, EmailUsage usage, long recentFailures) {}
