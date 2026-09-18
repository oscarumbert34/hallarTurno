package com.turnero.booking;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.business.Business;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class BusinessAgendaServiceTests {

    private final BookingRepository bookingRepository = org.mockito.Mockito.mock(BookingRepository.class);
    private final ObjectProvider<BookingRepository> bookingRepositoryProvider = org.mockito.Mockito.mock(ObjectProvider.class);
    private final BusinessAgendaEmailService emailService = org.mockito.Mockito.mock(BusinessAgendaEmailService.class);
    private final BusinessAgendaProperties properties = new BusinessAgendaProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T21:00:00Z"), ZoneOffset.UTC);
    private final BusinessAgendaService service = new BusinessAgendaService(
            bookingRepositoryProvider, emailService, properties, clock);

    BusinessAgendaServiceTests() {
        when(bookingRepositoryProvider.getIfAvailable()).thenReturn(bookingRepository);
    }

    @Test
    void groupsTomorrowActiveBookingsAndSendsOneEmailPerBusiness() {
        Business firstBusiness = business();
        Business secondBusiness = business();
        Booking first = booking(firstBusiness);
        Booking second = booking(firstBusiness);
        Booking third = booking(secondBusiness);
        when(bookingRepository.findBusinessAgendaCandidates(
                Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION),
                Instant.parse("2026-09-08T03:00:00Z"),
                Instant.parse("2026-09-09T03:00:00Z")
        )).thenReturn(List.of(first, second, third));

        service.sendTomorrowAgendas();

        verify(emailService).sendAgenda(LocalDate.of(2026, 9, 8), List.of(first, second));
        verify(emailService).sendAgenda(LocalDate.of(2026, 9, 8), List.of(third));
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.setEnabled(false);

        service.sendTomorrowAgendas();

        verify(bookingRepository, never()).findBusinessAgendaCandidates(
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    private Business business() {
        Business business = org.mockito.Mockito.mock(Business.class);
        when(business.getId()).thenReturn(UUID.randomUUID());
        return business;
    }

    private Booking booking(Business business) {
        Booking booking = org.mockito.Mockito.mock(Booking.class);
        when(booking.getBusiness()).thenReturn(business);
        return booking;
    }
}
