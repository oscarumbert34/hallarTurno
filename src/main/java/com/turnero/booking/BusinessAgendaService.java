package com.turnero.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;

@Service
public class BusinessAgendaService {

    private static final Logger log = LoggerFactory.getLogger(BusinessAgendaService.class);

    private final ObjectProvider<BookingRepository> bookingRepositoryProvider;
    private final BusinessAgendaEmailService emailService;
    private final BusinessAgendaProperties properties;
    private final Clock clock;

    public BusinessAgendaService(
            ObjectProvider<BookingRepository> bookingRepositoryProvider,
            BusinessAgendaEmailService emailService,
            BusinessAgendaProperties properties,
            Clock clock
    ) {
        this.bookingRepositoryProvider = bookingRepositoryProvider;
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void sendTomorrowAgendas() {
        if (!properties.isEnabled()) {
            log.debug("business agenda emails skipped because they are disabled");
            return;
        }

        BookingRepository bookingRepository = bookingRepositoryProvider.getIfAvailable();
        if (bookingRepository == null) {
            log.debug("business agenda emails skipped because the booking repository is not available");
            return;
        }

        ZoneId zoneId = ZoneId.of(properties.getZoneId());
        LocalDate agendaDate = LocalDate.now(clock.withZone(zoneId)).plusDays(1);
        Instant from = agendaDate.atStartOfDay(zoneId).toInstant();
        Instant to = agendaDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<Booking> bookings = bookingRepository.findBusinessAgendaCandidates(
                Set.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_CONFIRMATION), from, to);

        Map<UUID, List<Booking>> byBusiness = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            byBusiness.computeIfAbsent(booking.getBusiness().getId(), ignored -> new java.util.ArrayList<>())
                    .add(booking);
        }

        int sent = 0;
        for (List<Booking> businessBookings : byBusiness.values()) {
            if (emailService.sendAgenda(agendaDate, businessBookings)) {
                sent++;
            }
        }
        log.info("business agendas processed agendaDate={} businesses={} bookings={} sent={}",
                agendaDate, byBusiness.size(), bookings.size(), sent);
    }
}
