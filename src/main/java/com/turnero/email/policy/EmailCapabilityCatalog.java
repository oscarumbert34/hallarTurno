package com.turnero.email.policy;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EmailCapabilityCatalog {
    private static final Set<EmailType> ESSENTIAL = EnumSet.of(
            EmailType.BOOKING_CONFIRMATION, EmailType.BOOKING_RESCHEDULE, EmailType.BUSINESS_DAILY_AGENDA);
    private static final Set<EmailType> COMPLETE = EnumSet.allOf(EmailType.class);

    public boolean includes(EmailAddon addon, EmailType type) {
        return switch (addon) {
            case NONE -> false;
            case ESSENTIAL -> ESSENTIAL.contains(type);
            case COMPLETE -> COMPLETE.contains(type);
        };
    }

    public Set<EmailType> includedTypes(EmailAddon addon) {
        return switch (addon) {
            case NONE -> Set.of();
            case ESSENTIAL -> Set.copyOf(ESSENTIAL);
            case COMPLETE -> Set.copyOf(COMPLETE);
        };
    }
}
