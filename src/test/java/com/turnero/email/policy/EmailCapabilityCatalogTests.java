package com.turnero.email.policy;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class EmailCapabilityCatalogTests {
    private final EmailCapabilityCatalog catalog = new EmailCapabilityCatalog();

    @Test
    void noneIncludesNoAutomation() {
        for (EmailType type : EmailType.values()) assertThat(catalog.includes(EmailAddon.NONE, type)).isFalse();
    }

    @Test
    void essentialIncludesConfirmationRescheduleAndAgendaOnly() {
        assertThat(catalog.includedTypes(EmailAddon.ESSENTIAL)).containsExactlyInAnyOrder(
                EmailType.BOOKING_CONFIRMATION, EmailType.BOOKING_RESCHEDULE, EmailType.BUSINESS_DAILY_AGENDA);
    }

    @Test
    void completeIncludesEveryAutomation() {
        for (EmailType type : EmailType.values()) assertThat(catalog.includes(EmailAddon.COMPLETE, type)).isTrue();
    }

    @Test
    void limitsAreIndependentFromPrices() {
        assertThat(EmailAddon.NONE.monthlyLimit()).isZero();
        assertThat(EmailAddon.ESSENTIAL.monthlyLimit()).isEqualTo(400);
        assertThat(EmailAddon.COMPLETE.monthlyLimit()).isEqualTo(1300);
    }
}
