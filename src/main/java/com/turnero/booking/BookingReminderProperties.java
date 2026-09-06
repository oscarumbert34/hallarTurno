package com.turnero.booking;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booking.reminders")
public class BookingReminderProperties {

    private boolean enabled = true;
    private String cron = "0 0 18,21 * * *";
    private String zoneId = "America/Argentina/Buenos_Aires";
    private int targetDaysOffset = 1;
    private long minimumHoursBeforeStart = 12;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron == null || cron.isBlank() ? "0 0 18,21 * * *" : cron.trim();
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId == null || zoneId.isBlank() ? "America/Argentina/Buenos_Aires" : zoneId.trim();
    }

    public int getTargetDaysOffset() {
        return targetDaysOffset;
    }

    public void setTargetDaysOffset(int targetDaysOffset) {
        this.targetDaysOffset = targetDaysOffset;
    }

    public long getMinimumHoursBeforeStart() {
        return minimumHoursBeforeStart;
    }

    public void setMinimumHoursBeforeStart(long minimumHoursBeforeStart) {
        this.minimumHoursBeforeStart = minimumHoursBeforeStart;
    }

    public Duration getMinimumTimeBeforeStart() {
        return Duration.ofHours(this.minimumHoursBeforeStart);
    }
}
