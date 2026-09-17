package com.turnero.storage;

public interface ObjectStorageService {

    void upload(String key, byte[] content, String contentType);

    void delete(String key);

    String signedGetUrl(String key);
}
