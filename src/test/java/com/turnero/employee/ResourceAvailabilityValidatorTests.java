package com.turnero.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.turnero.common.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceAvailabilityValidatorTests {

    private final ResourceAvailabilityValidator validator = new ResourceAvailabilityValidator();

    @Test
    void acceptsClosedDaysAndNonOverlappingWorkIntervals() {
        List<BookableResource.WorkingIntervalValue> intervals = validator.validateSchedule(List.of(
                new ResourceScheduleRequest(DayOfWeek.MONDAY, List.of(
                        new ResourceIntervalRequest(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        new ResourceIntervalRequest(LocalTime.of(14, 0), LocalTime.of(18, 0))
                )),
                new ResourceScheduleRequest(DayOfWeek.TUESDAY, List.of())
        ));

        assertThat(intervals).hasSize(2);
    }

    @Test
    void rejectsInvalidWorkInterval() {
        assertThatThrownBy(() -> validator.validateSchedule(List.of(
                new ResourceScheduleRequest(DayOfWeek.MONDAY, List.of(
                        new ResourceIntervalRequest(LocalTime.of(10, 0), LocalTime.of(10, 0))
                ))
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("Resource schedule interval start must be before end");
    }

    @Test
    void rejectsOverlappingWorkIntervals() {
        assertThatThrownBy(() -> validator.validateSchedule(List.of(
                new ResourceScheduleRequest(DayOfWeek.MONDAY, List.of(
                        new ResourceIntervalRequest(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        new ResourceIntervalRequest(LocalTime.of(11, 0), LocalTime.of(13, 0))
                ))
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("Resource schedule intervals overlap for MONDAY");
    }

    @Test
    void rejectsInvalidAndOverlappingAbsences() {
        assertThatThrownBy(() -> validator.validateAbsences(List.of(
                new ResourceAbsenceRequest(LocalDate.of(2026, 9, 1), LocalTime.of(12, 0), LocalTime.of(11, 0))
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("Resource absence start must be before end");

        assertThatThrownBy(() -> validator.validateAbsences(List.of(
                new ResourceAbsenceRequest(LocalDate.of(2026, 9, 1), LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new ResourceAbsenceRequest(LocalDate.of(2026, 9, 1), LocalTime.of(11, 0), LocalTime.of(13, 0))
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("Resource absences overlap for 2026-09-01");
    }
}
