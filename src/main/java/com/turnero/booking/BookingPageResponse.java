package com.turnero.booking;

import java.util.List;

public record BookingPageResponse(
        int page,
        int size,
        int maxSize,
        long totalElements,
        int totalPages,
        boolean hasMore,
        String sort,
        List<BookingResponse> results
) {
    public BookingPageResponse {
        results = List.copyOf(results);
    }
}
