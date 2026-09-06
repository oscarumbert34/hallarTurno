package com.turnero.booking;

import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class BookingReminderScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BookingReminderScheduler.class);

    private final BookingReminderService bookingReminderService;
    private final BookingReminderProperties properties;

    public BookingReminderScheduler(
            final BookingReminderService bookingReminderService,
            final BookingReminderProperties properties
    ) {
        this.bookingReminderService = bookingReminderService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        log.info(
                "booking reminder scheduler registered enabled={} cron={} zoneId={} targetDaysOffset={} minimumHoursBeforeStart={}",
                this.properties.isEnabled(),
                this.properties.getCron(),
                this.properties.getZoneId(),
                this.properties.getTargetDaysOffset(),
                this.properties.getMinimumHoursBeforeStart()
        );
        taskRegistrar.addTriggerTask(this::runReminderJob, this::nextExecution);
    }

    private void runReminderJob() {
        try {
            this.bookingReminderService.sendDueReminders();
        } catch (final RuntimeException exception) {
            log.error("booking reminder job failed", exception);
        }
    }

    private java.time.Instant nextExecution(final TriggerContext triggerContext) {
        final Trigger trigger = new CronTrigger(this.properties.getCron(), ZoneId.of(this.properties.getZoneId()));
        return trigger.nextExecution(triggerContext);
    }
}
