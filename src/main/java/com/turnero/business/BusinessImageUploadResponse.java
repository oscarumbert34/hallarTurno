package com.turnero.business;

public record BusinessImageUploadResponse(
        String imageKey,
        String imageUrl,
        String contentType,
        long size
) {
}
