package com.turnero.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String bucket;
    private String accessKey;
    private String secretKey;
    private String region = "auto";
    private String endpoint;
    private String urlStyle = "virtual";
    private Duration signedUrlDuration = Duration.ofHours(1);

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getUrlStyle() { return urlStyle; }
    public void setUrlStyle(String urlStyle) { this.urlStyle = urlStyle; }
    public Duration getSignedUrlDuration() { return signedUrlDuration; }
    public void setSignedUrlDuration(Duration signedUrlDuration) { this.signedUrlDuration = signedUrlDuration; }

    public boolean isConfigured() {
        return hasText(bucket) && hasText(accessKey) && hasText(secretKey) && hasText(endpoint);
    }

    public boolean isPathStyle() {
        return "path".equalsIgnoreCase(urlStyle) || "path-style".equalsIgnoreCase(urlStyle);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
