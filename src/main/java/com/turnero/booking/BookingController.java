package com.turnero.booking;

import com.turnero.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return bookingService.create(request, currentUser);
    }

    
    @PostMapping("/public/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createPublic(@Valid @RequestBody BookingRequest request) {
        return bookingService.createPublic(request);
    }

@PostMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return bookingService.cancel(id, currentUser);
    }

    @GetMapping("/businesses/{businessId}/bookings")
    public BookingPageResponse findByBusiness(
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return bookingService.findByBusiness(businessId, currentUser, page, size, date);
    }
}



