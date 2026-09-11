package com.turnero.booking;

import com.turnero.business.BusinessConfigurationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingReminderService {

    private static final Logger log = LoggerFactory.getLogger(BookingReminderService.class);

    private final ObjectProvider<BookingRepository> bookingRepositoryProvider;
    private final BookingReminderEmailService emailService;
    private final BookingConfirmationEmailService confirmationEmailService;
    private final BookingActionTokenService actionTokenService;
    private final ObjectProvider<BusinessConfigurationRepository> configurationRepositoryProvider;
    private final BookingReminderProperties properties;
    private final Clock clock;

    public BookingReminderService(
            final ObjectProvider<BookingRepository> bookingRepositoryProvider,
            final BookingReminderEmailService emailService,
            final BookingConfirmationEmailService confirmationEmailService,
            final BookingActionTokenService actionTokenService,
            final ObjectProvider<BusinessConfigurationRepository> configurationRepositoryProvider,
            final BookingReminderProperties properties,
            final Clock clock
    ) {
        this.bookingRepositoryProvider = bookingRepositoryProvider;
        this.emailService = emailService;
        this.confirmationEmailService = confirmationEmailService;
        this.actionTokenService = actionTokenService;
        this.configurationRepositoryProvider = configurationRepositoryProvider;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void sendDueReminders() {
        if (!this.properties.isEnabled()) {
            log.debug("booking reminders skipped because they are disabled");
            return;
        }

        final BookingRepository bookingRepository = this.bookingRepositoryProvider.getIfAvailable();
        final BusinessConfigurationRepository configurationRepository = this.configurationRepositoryProvider.getIfAvailable();
        if (bookingRepository == null || configurationRepository == null) {
            log.debug("booking notifications skipped because a repository is not available");
            return;
        }

        final ZoneId zoneId = ZoneId.of(this.properties.getZoneId());
        final Instant now = Instant.now(this.clock);
        final LocalDate targetDate = LocalDate.now(this.clock.withZone(zoneId))
                .plusDays(this.properties.getTargetDaysOffset());
        final Instant targetStartsAtFrom = targetDate.atStartOfDay(zoneId).toInstant();
        final Instant minimumStartsAt = now.plus(this.properties.getMinimumTimeBeforeStart());
        final Instant startsAtFrom = minimumStartsAt.isAfter(targetStartsAtFrom)
                ? minimumStartsAt
                : targetStartsAtFrom;
        final Instant startsAtTo = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        if (!startsAtFrom.isBefore(startsAtTo)) {
            log.info(
                    "booking reminders processed targetDate={} candidates=0 sent=0 minimumStartsAt={} skippedReason=minimum-window-outside-target-date",
                    targetDate,
                    startsAtFrom
            );
            return;
        }
        final List<Booking> candidates = bookingRepository.findReminderCandidates(
                Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION),
                startsAtFrom,
                startsAtTo
        );
        int sent = 0;
        for (Booking booking : candidates) {
            final boolean confirmationEnabled = configurationRepository.findById(booking.getBusiness().getId())
                    .map(configuration -> configuration.isAppointmentConfirmationEnabled())
                    .orElse(false);
            final boolean emailSent = confirmationEnabled
                    ? sendConfirmationIfPending(booking)
                    : sendReminderIfConfirmed(booking);
            if (emailSent) {
                booking.markReminderSent(Instant.now(this.clock));
                sent++;
            }
        }
        log.info(
                "booking reminders processed targetDate={} candidates={} sent={} minimumStartsAt={}",
                targetDate,
                candidates.size(),
                sent,
                startsAtFrom
        );
    }

    private boolean sendConfirmationIfPending(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_CONFIRMATION) {
            return false;
        }
        final String actionToken = actionTokenService.issueFor(booking);
        final boolean sent = confirmationEmailService.sendConfirmation(booking, actionToken);
        if (!sent) {
            actionTokenService.discard(actionToken);
        }
        return sent;
    }

    private boolean sendReminderIfConfirmed(Booking booking) {
        return booking.getStatus() == BookingStatus.CONFIRMED && emailService.sendReminder(booking);
    }
}
