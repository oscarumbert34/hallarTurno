package com.turnero.booking;

import java.util.List;

public record BookingPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<BookingResponse> results
) {
}
