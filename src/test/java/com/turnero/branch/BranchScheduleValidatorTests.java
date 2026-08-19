package com.turnero.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.turnero.common.ApiException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchScheduleValidatorTests {

    private final BranchScheduleValidator validator = new BranchScheduleValidator();

    @Test
    void acceptsClosedDaysAndMultipleNonOverlappingIntervals() {
        List<Branch.OpeningIntervalValue> intervals = validator.validate(List.of(
                new BranchScheduleRequest(DayOfWeek.MONDAY, List.of(
                        new OpeningIntervalRequest(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        new OpeningIntervalRequest(LocalTime.of(14, 0), LocalTime.of(18, 0))
                )),
                new BranchScheduleRequest(DayOfWeek.TUESDAY, List.of())
        ));

        assertThat(intervals).hasSize(2);
        assertThat(intervals).extracting(Branch.OpeningIntervalValue::dayOfWeek)
                .containsOnly(DayOfWeek.MONDAY);
    }

    @Test
    void rejectsIntervalsWhereStartIsNotBeforeEnd() {
        assertThatThrownBy(() -> validator.validate(List.of(
                new BranchScheduleRequest(DayOfWeek.MONDAY, List.of(
                        new OpeningIntervalRequest(LocalTime.of(9, 0), LocalTime.of(9, 0))
                ))
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("Schedule interval start must be before end");
    }

    @Test
    void rejectsOverlappingIntervals() {
        assertThatThrownBy(() -> validator.validate(List.of(
                new BranchScheduleRequest(DayOfWeek.MONDAY, List.of(
                        new OpeningIntervalRequest(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        new OpeningIntervalRequest(LocalTime.of(11, 0), LocalTime.of(15, 0))
                ))
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("Schedule intervals overlap for MONDAY");
    }

    @Test
    void rejectsDuplicatedDays() {
        assertThatThrownBy(() -> validator.validate(List.of(
                new BranchScheduleRequest(DayOfWeek.MONDAY, List.of()),
                new BranchScheduleRequest(DayOfWeek.MONDAY, List.of())
        )))
                .isInstanceOf(ApiException.class)
                .hasMessage("Schedule contains duplicated days");
    }
}
