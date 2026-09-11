package com.turnero.employee;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record ResourceAbsenceRequest(
        @NotNull
        LocalDate date,

        Boolean allDay,

        @JsonAlias("startTime")
        LocalTime startsAt,

        @JsonAlias("endTime")
        LocalTime endsAt
) {
    public ResourceAbsenceRequest(LocalDate date, LocalTime startsAt, LocalTime endsAt) {
        this(date, false, startsAt, endsAt);
    }

    public boolean isAllDay() {
        return Boolean.TRUE.equals(allDay);
    }
}
