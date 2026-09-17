package com.turnero.storage;

import com.turnero.common.ApiException;
import java.net.URI;
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

    private final StorageProperties properties;

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
                    .build(), RequestBody.fromBytes(content));
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
        try (S3Presigner presigner = presigner()) {
            GetObjectRequest getObject = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(properties.getSignedUrlDuration())
                            .getObjectRequest(getObject)
                            .build())
                    .url()
                    .toExternalForm();
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
}
