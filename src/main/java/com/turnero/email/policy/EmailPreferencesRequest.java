package com.turnero.email.policy;

public record EmailPreferencesRequest(boolean confirmationEnabled, boolean dailyAgendaEnabled,
        boolean reminderActionEnabled, boolean cancellationEnabled) {}
