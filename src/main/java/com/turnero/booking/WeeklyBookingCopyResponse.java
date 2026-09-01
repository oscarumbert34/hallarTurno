package com.turnero.booking;

import java.time.LocalDate;
import java.util.List;

public record WeeklyBookingCopyResponse(
        LocalDate sourceWeekStart,
        LocalDate targetWeekStart,
        int sourceBookings,
        int createdCount,
        int skippedCount,
        int conflictCount,
        List<WeeklyBookingCopyCreated> created,
        List<WeeklyBookingCopySkipped> skipped,
        List<WeeklyBookingCopyConflict> conflicts
) {
    public WeeklyBookingCopyResponse {
        created = List.copyOf(created);
        skipped = List.copyOf(skipped);
        conflicts = List.copyOf(conflicts);
    }
}
