package com.turnero.branch;

import com.turnero.common.ApiException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class BranchScheduleValidator {

    List<Branch.OpeningIntervalValue> validate(List<BranchScheduleRequest> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return List.of();
        }

        EnumSet<DayOfWeek> seenDays = EnumSet.noneOf(DayOfWeek.class);
        List<Branch.OpeningIntervalValue> values = new ArrayList<>();
        for (BranchScheduleRequest daySchedule : schedule) {
            if (!seenDays.add(daySchedule.dayOfWeek())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule contains duplicated days");
            }

            List<OpeningIntervalRequest> intervals = daySchedule.intervals() == null
                    ? List.of()
                    : daySchedule.intervals().stream()
                            .sorted(Comparator.comparing(OpeningIntervalRequest::opensAt))
                            .toList();
            validateIntervals(daySchedule.dayOfWeek(), intervals);
            intervals.forEach(interval -> values.add(new Branch.OpeningIntervalValue(
                    daySchedule.dayOfWeek(),
                    interval.opensAt(),
                    interval.closesAt()
            )));
        }
        return values;
    }

    private void validateIntervals(DayOfWeek dayOfWeek, List<OpeningIntervalRequest> intervals) {
        OpeningIntervalRequest previous = null;
        for (OpeningIntervalRequest interval : intervals) {
            if (!interval.opensAt().isBefore(interval.closesAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule interval start must be before end");
            }
            if (previous != null && interval.opensAt().isBefore(previous.closesAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule intervals overlap for " + dayOfWeek);
            }
            previous = interval;
        }
    }
}
