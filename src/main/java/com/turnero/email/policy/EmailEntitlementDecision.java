package com.turnero.email.policy;

public record EmailEntitlementDecision(
        EmailDecisionCode code,
        QuotaSource quotaSource,
        int used,
        int limit,
        String reason
) {
    public boolean shouldSend() { return code == EmailDecisionCode.SEND; }
}
