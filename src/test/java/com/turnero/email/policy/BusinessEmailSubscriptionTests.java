package com.turnero.email.policy;

import static org.assertj.core.api.Assertions.assertThat;
import com.turnero.business.Business;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BusinessEmailSubscriptionTests {
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Test
    void newTrialProvidesProAndCompleteForThirtyDays() {
        var subscription = BusinessEmailSubscription.trial(Mockito.mock(Business.class), NOW);
        assertThat(subscription.getBasePlan()).isEqualTo(BasePlan.PRO);
        assertThat(subscription.effectiveAddon(NOW)).isEqualTo(EmailAddon.COMPLETE);
        assertThat(subscription.getTrialEndsAt()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void unselectedExpiredTrialFallsBackWithoutDeletingPreferences() {
        var subscription = BusinessEmailSubscription.trial(Mockito.mock(Business.class), NOW);
        subscription.updatePreferences(false, true, false, true);
        subscription.expireTrial(NOW.plus(30, ChronoUnit.DAYS));
        assertThat(subscription.getBasePlan()).isEqualTo(BasePlan.BASIC);
        assertThat(subscription.getAddon()).isEqualTo(EmailAddon.NONE);
        assertThat(subscription.isDailyAgendaEnabled()).isTrue();
        assertThat(subscription.isCancellationEnabled()).isTrue();
    }

    @Test
    void upgradeIsImmediateAndDowngradeIsDeferred() {
        var subscription = BusinessEmailSubscription.trial(Mockito.mock(Business.class), NOW);
        subscription.selectAddon(EmailAddon.NONE, NOW.plusSeconds(1));
        assertThat(subscription.getPendingAddon()).isEqualTo(EmailAddon.NONE);
        assertThat(subscription.effectiveAddon(NOW.plusSeconds(2))).isEqualTo(EmailAddon.COMPLETE);
    }
}
