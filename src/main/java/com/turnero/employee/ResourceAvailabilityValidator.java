package com.turnero.employee;

import com.turnero.common.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ResourceAvailabilityValidator {

    List<BookableResource.WorkingIntervalValue> validateSchedule(List<ResourceScheduleRequest> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return List.of();
        }

        EnumSet<DayOfWeek> seenDays = EnumSet.noneOf(DayOfWeek.class);
        List<BookableResource.WorkingIntervalValue> values = new ArrayList<>();
        for (ResourceScheduleRequest daySchedule : schedule) {
            if (!seenDays.add(daySchedule.dayOfWeek())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Resource schedule contains duplicated days");
            }
            List<ResourceIntervalRequest> intervals = daySchedule.intervals() == null
                    ? List.of()
                    : daySchedule.intervals().stream()
                            .sorted(Comparator.comparing(ResourceIntervalRequest::startsAt))
                            .toList();
            validateIntervals(intervals, "Resource schedule interval start must be before end",
                    "Resource schedule intervals overlap for " + daySchedule.dayOfWeek());
            intervals.forEach(interval -> values.add(new BookableResource.WorkingIntervalValue(
                    daySchedule.dayOfWeek(),
                    interval.startsAt(),
                    interval.endsAt()
            )));
        }
        return values;
    }

    List<BookableResource.AbsenceValue> validateAbsences(List<ResourceAbsenceRequest> absences) {
        if (absences == null || absences.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, List<ResourceAbsenceRequest>> absencesByDate = absences.stream()
                .collect(Collectors.groupingBy(ResourceAbsenceRequest::date));
        List<BookableResource.AbsenceValue> values = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ResourceAbsenceRequest>> entry : absencesByDate.entrySet()) {
            List<ResourceAbsenceRequest> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(ResourceAbsenceRequest::startsAt))
                    .toList();
            validateAbsencesForDate(entry.getKey(), sorted);
            sorted.forEach(absence -> values.add(new BookableResource.AbsenceValue(
                    absence.date(),
                    absence.startsAt(),
                    absence.endsAt()
            )));
        }
        return values;
    }

    private void validateIntervals(List<ResourceIntervalRequest> intervals, String invalidMessage, String overlapMessage) {
        ResourceIntervalRequest previous = null;
        for (ResourceIntervalRequest interval : intervals) {
            if (!interval.startsAt().isBefore(interval.endsAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, invalidMessage);
            }
            if (previous != null && interval.startsAt().isBefore(previous.endsAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, overlapMessage);
            }
            previous = interval;
        }
    }

    private void validateAbsencesForDate(LocalDate date, List<ResourceAbsenceRequest> absences) {
        ResourceAbsenceRequest previous = null;
        for (ResourceAbsenceRequest absence : absences) {
            if (!absence.startsAt().isBefore(absence.endsAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Resource absence start must be before end");
            }
            if (previous != null && absence.startsAt().isBefore(previous.endsAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Resource absences overlap for " + date);
            }
            previous = absence;
        }
    }
}
