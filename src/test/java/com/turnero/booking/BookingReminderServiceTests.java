package com.turnero.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.branch.Branch;
import com.turnero.business.Business;
import com.turnero.business.BusinessConfiguration;
import com.turnero.business.BusinessConfigurationRepository;
import com.turnero.employee.BookableResource;
import com.turnero.service.ServiceOffering;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class BookingReminderServiceTests {

    private final BookingRepository bookingRepository = org.mockito.Mockito.mock(BookingRepository.class);
    private final ObjectProvider<BookingRepository> bookingRepositoryProvider = org.mockito.Mockito.mock(ObjectProvider.class);
    private final BookingReminderEmailService emailService = org.mockito.Mockito.mock(BookingReminderEmailService.class);
    private final BookingConfirmationEmailService confirmationEmailService = org.mockito.Mockito.mock(BookingConfirmationEmailService.class);
    private final BookingActionTokenService actionTokenService = org.mockito.Mockito.mock(BookingActionTokenService.class);
    private final BusinessConfigurationRepository configurationRepository = org.mockito.Mockito.mock(BusinessConfigurationRepository.class);
    private final ObjectProvider<BusinessConfigurationRepository> configurationRepositoryProvider = org.mockito.Mockito.mock(ObjectProvider.class);
    private final BookingReminderProperties properties = new BookingReminderProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T17:30:00Z"), ZoneOffset.UTC);
    private final BookingReminderService service = new BookingReminderService(
            bookingRepositoryProvider,
            emailService,
            confirmationEmailService,
            actionTokenService,
            configurationRepositoryProvider,
            properties,
            clock
    );

    BookingReminderServiceTests() {
        when(configurationRepositoryProvider.getIfAvailable()).thenReturn(configurationRepository);
    }

    @Test
    void sendsRemindersForTomorrowBookingsAtLeastTwelveHoursAwayAndMarksSuccessfulBookings() {
        Booking booking = booking();
        when(bookingRepositoryProvider.getIfAvailable()).thenReturn(bookingRepository);
        when(bookingRepository.findReminderCandidates(
                java.util.Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION),
                Instant.parse("2026-09-08T05:30:00Z"),
                Instant.parse("2026-09-09T03:00:00Z")
        )).thenReturn(List.of(booking));
        when(emailService.sendReminder(booking)).thenReturn(true);

        service.sendDueReminders();

        assertThat(booking.getReminderSentAt()).isEqualTo(Instant.parse("2026-09-07T17:30:00Z"));
    }

    @Test
    void usesTargetDaysOffsetToChooseReminderDate() {
        properties.setTargetDaysOffset(2);
        when(bookingRepositoryProvider.getIfAvailable()).thenReturn(bookingRepository);
        ArgumentCaptor<Instant> startsAtFrom = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> startsAtTo = ArgumentCaptor.forClass(Instant.class);

        service.sendDueReminders();

        verify(bookingRepository).findReminderCandidates(
                org.mockito.Mockito.eq(java.util.Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION)),
                startsAtFrom.capture(),
                startsAtTo.capture()
        );
        assertThat(startsAtFrom.getValue()).isEqualTo(Instant.parse("2026-09-09T03:00:00Z"));
        assertThat(startsAtTo.getValue()).isEqualTo(Instant.parse("2026-09-10T03:00:00Z"));
    }

    @Test
    void usesConfiguredMinimumHoursBeforeBookingStart() {
        properties.setMinimumHoursBeforeStart(15);
        when(bookingRepositoryProvider.getIfAvailable()).thenReturn(bookingRepository);
        ArgumentCaptor<Instant> startsAtFrom = ArgumentCaptor.forClass(Instant.class);

        service.sendDueReminders();

        verify(bookingRepository).findReminderCandidates(
                org.mockito.Mockito.eq(java.util.Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION)),
                startsAtFrom.capture(),
                org.mockito.Mockito.eq(Instant.parse("2026-09-09T03:00:00Z"))
        );
        assertThat(startsAtFrom.getValue()).isEqualTo(Instant.parse("2026-09-08T08:30:00Z"));
    }

    @Test
    void skipsFetchingBookingsWhenMinimumWindowIsOutsideTargetDate() {
        properties.setMinimumHoursBeforeStart(36);
        when(bookingRepositoryProvider.getIfAvailable()).thenReturn(bookingRepository);

        service.sendDueReminders();

        verify(bookingRepository, never()).findReminderCandidates(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        );
    }

    @Test
    void doesNotFetchBookingsWhenDisabled() {
        properties.setEnabled(false);

        service.sendDueReminders();

        verify(bookingRepository, never()).findReminderCandidates(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        );
    }

    @Test
    void doesNotMarkBookingWhenEmailFails() {
        Booking booking = booking();
        when(bookingRepositoryProvider.getIfAvailable()).thenReturn(bookingRepository);
        when(bookingRepository.findReminderCandidates(
                java.util.Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION),
                Instant.parse("2026-09-08T05:30:00Z"),
                Instant.parse("2026-09-09T03:00:00Z")
        )).thenReturn(List.of(booking));
        when(emailService.sendReminder(booking)).thenReturn(false);

        service.sendDueReminders();

        assertThat(booking.getReminderSentAt()).isNull();
    }

    @Test
    void sendsConfirmationInsteadOfReminderForEnabledBusinessAndMarksItProcessed() {
        Booking booking = booking(BookingStatus.PENDING_CONFIRMATION);
        BusinessConfiguration configuration = BusinessConfiguration.createDefault(booking.getBusiness());
        configuration.updateAppointmentConfirmationEnabled(true);
        when(bookingRepositoryProvider.getIfAvailable()).thenReturn(bookingRepository);
        when(configurationRepository.findById(booking.getBusiness().getId())).thenReturn(java.util.Optional.of(configuration));
        when(bookingRepository.findReminderCandidates(
                java.util.Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION),
                Instant.parse("2026-09-08T05:30:00Z"),
                Instant.parse("2026-09-09T03:00:00Z")
        )).thenReturn(List.of(booking));
        when(actionTokenService.issueFor(booking)).thenReturn("action-token");
        when(confirmationEmailService.sendConfirmation(booking, "action-token")).thenReturn(true);

        service.sendDueReminders();

        verify(confirmationEmailService).sendConfirmation(booking, "action-token");
        verify(emailService, never()).sendReminder(booking);
        assertThat(booking.getReminderSentAt()).isEqualTo(Instant.parse("2026-09-07T17:30:00Z"));
    }

    private Booking booking() {
        return booking(BookingStatus.CONFIRMED);
    }

    private Booking booking(BookingStatus status) {
        Branch branch = org.mockito.Mockito.mock(Branch.class);
        Business business = org.mockito.Mockito.mock(Business.class);
        ServiceOffering serviceOffering = org.mockito.Mockito.mock(ServiceOffering.class);
        BookableResource resource = org.mockito.Mockito.mock(BookableResource.class);
        return Booking.create(
                branch,
                business,
                null,
                serviceOffering,
                resource,
                Instant.parse("2026-09-08T13:00:00Z"),
                Instant.parse("2026-09-08T13:30:00Z"),
                "Corte",
                "Juan",
                "Ana",
                "+54 11 5555-1234",
                "ana@example.com",
                null,
                30,
                BigDecimal.valueOf(1500),
                "ARS",
                status
        );
    }
}
