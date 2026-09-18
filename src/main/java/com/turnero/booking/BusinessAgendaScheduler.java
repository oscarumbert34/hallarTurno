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
public class BusinessAgendaScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BusinessAgendaScheduler.class);

    private final BusinessAgendaService agendaService;
    private final BusinessAgendaProperties properties;

    public BusinessAgendaScheduler(BusinessAgendaService agendaService, BusinessAgendaProperties properties) {
        this.agendaService = agendaService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        log.info("business agenda scheduler registered enabled={} cron={} zoneId={}",
                properties.isEnabled(), properties.getCron(), properties.getZoneId());
        taskRegistrar.addTriggerTask(this::runAgendaJob, this::nextExecution);
    }

    private void runAgendaJob() {
        try {
            agendaService.sendTomorrowAgendas();
        } catch (RuntimeException exception) {
            log.error("business agenda job failed", exception);
        }
    }

    private java.time.Instant nextExecution(TriggerContext triggerContext) {
        Trigger trigger = new CronTrigger(properties.getCron(), ZoneId.of(properties.getZoneId()));
        return trigger.nextExecution(triggerContext);
    }
}
