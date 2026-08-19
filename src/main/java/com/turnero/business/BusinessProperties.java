package com.turnero.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "business")
public class BusinessProperties {

    private BusinessStatus initialStatus = BusinessStatus.ACTIVE;

    public BusinessStatus getInitialStatus() {
        return initialStatus;
    }

    public void setInitialStatus(BusinessStatus initialStatus) {
        this.initialStatus = initialStatus;
    }
}
