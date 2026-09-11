package com.turnero.employee;

import java.time.LocalDate;
import java.time.LocalTime;

public record ResourceAbsenceResponse(LocalDate date, boolean allDay, LocalTime startsAt, LocalTime endsAt) {

    static ResourceAbsenceResponse from(ResourceAbsence absence) {
        return new ResourceAbsenceResponse(absence.getDate(), absence.isAllDay(), absence.getStartsAt(), absence.getEndsAt());
    }
}
