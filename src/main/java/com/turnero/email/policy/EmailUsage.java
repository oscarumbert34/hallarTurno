package com.turnero.email.policy;

public record EmailUsage(long used, int limit, long remaining, long growthAgendaUsed, int growthAgendaLimit) {}
