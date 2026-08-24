package com.turnero.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        String requestId,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {

    public static ApiError of(int status, String error, String message, String path, List<String> details) {
        return of(null, status, error, message, path, details);
    }

    public static ApiError of(
            String requestId,
            int status,
            String error,
            String message,
            String path,
            List<String> details
    ) {
        return new ApiError(
                Instant.now(),
                requestId,
                status,
                error,
                message,
                path,
                details == null ? List.of() : List.copyOf(details)
        );
    }
}
