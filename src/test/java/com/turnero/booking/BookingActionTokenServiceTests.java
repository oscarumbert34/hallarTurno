package com.turnero.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.branch.Branch;
import com.turnero.business.Business;
import com.turnero.common.ApiException;
import com.turnero.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

class BookingActionTokenServiceTests {

    private static final String RAW_TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Instant NOW = Instant.parse("2026-09-10T18:00:00Z");
    private final BookingActionTokenRepository repository = mock(BookingActionTokenRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<BookingActionTokenRepository> repositoryProvider = mock(ObjectProvider.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final BookingActionTokenService service = new BookingActionTokenService(
            repositoryProvider,
            Clock.fixed(NOW, ZoneOffset.UTC),
            eventPublisher
    );

    BookingActionTokenServiceTests() {
        when(repositoryProvider.getObject()).thenReturn(repository);
    }

    @Test
    void confirmsPendingBookingAndConsumesToken() {
        Booking booking = booking(BookingStatus.PENDING_CONFIRMATION);
        BookingActionToken token = new BookingActionToken(booking, "hash", NOW.plusSeconds(3600));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        PublicBookingActionResponse response = service.confirm(RAW_TOKEN);

        verify(booking).confirm();
        assertThat(token.isUsed()).isTrue();
        assertThat(response.tokenValid()).isFalse();
    }

    @Test
    void rejectsAnAlreadyUsedToken() {
        BookingActionToken token = new BookingActionToken(
                booking(BookingStatus.PENDING_CONFIRMATION), "hash", NOW.plusSeconds(3600));
        token.markUsed(NOW.minusSeconds(1));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.cancel(RAW_TOKEN))
                .isInstanceOf(ApiException.class)
                .hasMessage("Appointment has already been managed");
    }

    @Test
    void cancellationPublishesAnAppointmentCancelledEvent() {
        Booking booking = booking(BookingStatus.PENDING_CONFIRMATION);
        BookingActionToken token = new BookingActionToken(booking, "hash", NOW.plusSeconds(3600));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        service.cancel(RAW_TOKEN);

        verify(booking).cancel(null, NOW);
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(AppointmentCancelledEvent.class));
    }

    @Test
    void expiredTokenCanBeDisplayedButCannotBeUsed() {
        BookingActionToken token = new BookingActionToken(
                booking(BookingStatus.PENDING_CONFIRMATION), "hash", NOW);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThat(service.get(RAW_TOKEN).tokenValid()).isFalse();
        assertThatThrownBy(() -> service.confirm(RAW_TOKEN))
                .isInstanceOf(ApiException.class)
                .hasMessage("Appointment action token expired");
    }

    private Booking booking(BookingStatus status) {
        Booking booking = mock(Booking.class);
        Branch branch = mock(Branch.class);
        Business business = mock(Business.class);
        User owner = mock(User.class);
        when(booking.getBranch()).thenReturn(branch);
        when(booking.getBusiness()).thenReturn(business);
        when(booking.getId()).thenReturn(java.util.UUID.randomUUID());
        when(booking.getStartsAt()).thenReturn(Instant.parse("2026-09-15T19:30:00Z"));
        when(booking.getStatus()).thenReturn(status, BookingStatus.CONFIRMED);
        when(booking.getServiceNameSnapshot()).thenReturn("Podologia");
        when(booking.getResourceNameSnapshot()).thenReturn("Consultorio 1");
        when(booking.getCustomerNameSnapshot()).thenReturn("Ana Pérez");
        when(branch.getZoneId()).thenReturn("America/Argentina/Buenos_Aires");
        when(branch.getName()).thenReturn("Centro");
        when(business.getName()).thenReturn("Centro Ejemplo");
        when(business.getContactEmail()).thenReturn("turnos@centro.test");
        when(business.getOwner()).thenReturn(owner);
        return booking;
    }
}
