package com.turnero.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "brevo")
public class BrevoProperties {

    private String apiKey = "";
    private String fromEmail = "";
    private String fromName = "HallarTurno";
    private String baseUrl = "https://api.brevo.com";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName == null ? "" : fromName.trim();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !fromEmail.isBlank() && !baseUrl.isBlank();
    }
}
