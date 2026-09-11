package com.turnero.booking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/appointments/actions/{token}")
public class PublicBookingActionController {

    private final BookingActionTokenService service;

    public PublicBookingActionController(BookingActionTokenService service) {
        this.service = service;
    }

    @GetMapping
    public PublicBookingActionResponse get(@PathVariable String token) {
        return service.get(token);
    }

    @PostMapping("/confirm")
    public PublicBookingActionResponse confirm(@PathVariable String token) {
        return service.confirm(token);
    }

    @PostMapping("/cancel")
    public PublicBookingActionResponse cancel(@PathVariable String token) {
        return service.cancel(token);
    }
}
