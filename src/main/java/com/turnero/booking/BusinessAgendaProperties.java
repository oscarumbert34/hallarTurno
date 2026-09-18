package com.turnero.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booking.business-agenda")
public class BusinessAgendaProperties {

    private boolean enabled = true;
    private String cron = "0 0 18 * * *";
    private String zoneId = "America/Argentina/Buenos_Aires";

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
        this.cron = cron == null || cron.isBlank() ? "0 0 18 * * *" : cron.trim();
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId == null || zoneId.isBlank()
                ? "America/Argentina/Buenos_Aires"
                : zoneId.trim();
    }

}
