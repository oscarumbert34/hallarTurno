package com.turnero.email.policy;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(name = "entityManagerFactory")
public class EmailTrialExpirationService {
    private static final Logger log = LoggerFactory.getLogger(EmailTrialExpirationService.class);
    private final BusinessEmailSubscriptionRepository subscriptions;
    private final Clock clock;
    public EmailTrialExpirationService(BusinessEmailSubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions; this.clock = clock;
    }
    @Scheduled(cron = "${email.trial-expiration-cron:0 15 3 * * *}", zone = "UTC")
    @Transactional
    public void expireTrials() {
        Instant now = Instant.now(clock);
        var expired = subscriptions.findExpiredTrials(now);
        expired.forEach(s -> { s.expireTrial(now); log.info("email trial expired businessId={}", s.getBusinessId()); });
    }
}
