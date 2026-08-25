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
            if (!seenDays.add(daySchedule.day())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule contains duplicated days");
            }

            List<OpeningIntervalRequest> intervals = daySchedule.timeRanges() == null
                    ? List.of()
                    : daySchedule.timeRanges().stream()
                            .sorted(Comparator.comparing(OpeningIntervalRequest::start))
                            .toList();
            validateIntervals(daySchedule.day(), intervals);
            intervals.forEach(interval -> values.add(new Branch.OpeningIntervalValue(
                    daySchedule.day(),
                    interval.start(),
                    interval.end()
            )));
        }
        return values;
    }

    private void validateIntervals(DayOfWeek dayOfWeek, List<OpeningIntervalRequest> intervals) {
        OpeningIntervalRequest previous = null;
        for (OpeningIntervalRequest interval : intervals) {
            if (!interval.start().isBefore(interval.end())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule interval start must be before end");
            }
            if (previous != null && interval.start().equals(previous.start()) && interval.end().equals(previous.end())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule contains duplicated intervals for " + dayOfWeek);
            }
            if (previous != null && interval.start().isBefore(previous.end())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule intervals overlap for " + dayOfWeek);
            }
            previous = interval;
        }
    }
}
