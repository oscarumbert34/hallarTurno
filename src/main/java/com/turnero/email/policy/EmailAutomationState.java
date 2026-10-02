package com.turnero.email.policy;

public record EmailAutomationState(EmailType type, boolean available, boolean enabled) {}
