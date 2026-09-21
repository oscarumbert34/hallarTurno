package com.turnero.storage;

import com.turnero.common.ApiException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
public class RailwayObjectStorageService implements ObjectStorageService {

    private static final String IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final StorageProperties properties;
    private final ConcurrentMap<String, CachedSignedUrl> signedUrlCache = new ConcurrentHashMap<>();

    public RailwayObjectStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void upload(String key, byte[] content, String contentType) {
        try (S3Client client = s3Client()) {
            client.putObject(PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .cacheControl(IMAGE_CACHE_CONTROL)
                    .build(), RequestBody.fromBytes(content));
            this.signedUrlCache.remove(key);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        this.signedUrlCache.remove(key);
        try (S3Client client = s3Client()) {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public String signedGetUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        final Instant now = Instant.now();
        final CachedSignedUrl cached = this.signedUrlCache.get(key);
        if (cached != null && now.isBefore(cached.refreshAt())) {
            return cached.url();
        }
        try (S3Presigner presigner = presigner()) {
            GetObjectRequest getObject = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();
            final String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(properties.getSignedUrlDuration())
                            .getObjectRequest(getObject)
                            .build())
                    .url()
                    .toExternalForm();
            this.signedUrlCache.put(key, new CachedSignedUrl(url, refreshAt(now)));
            return url;
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private S3Client s3Client() {
        requireConfiguration();
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration())
                .build();
    }

    private S3Presigner presigner() {
        requireConfiguration();
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(s3Configuration())
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey()));
    }

    private S3Configuration s3Configuration() {
        return S3Configuration.builder().pathStyleAccessEnabled(properties.isPathStyle()).build();
    }

    private void requireConfiguration() {
        if (!properties.isConfigured()) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is unavailable");
    }

    private Instant refreshAt(Instant createdAt) {
        final Duration duration = properties.getSignedUrlDuration();
        final Duration safetyMargin = duration.compareTo(Duration.ofMinutes(10)) > 0
                ? Duration.ofMinutes(5)
                : duration.dividedBy(10);
        return createdAt.plus(duration.minus(safetyMargin));
    }

    private record CachedSignedUrl(String url, Instant refreshAt) {
    }
}
