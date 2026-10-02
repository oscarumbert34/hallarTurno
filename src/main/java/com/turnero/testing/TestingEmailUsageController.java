package com.turnero.testing;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.email.policy.EmailStatusResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("e2e & !prod")
@RestController
@RequestMapping("/api/v1/testing/businesses/{businessId}/email-usage")
public class TestingEmailUsageController {
    private final TestingEmailUsageService service;

    public TestingEmailUsageController(TestingEmailUsageService service) {
        this.service = service;
    }

    @PutMapping
    public EmailStatusResponse setUsage(
            @PathVariable UUID businessId,
            @Valid @RequestBody TestingEmailUsageRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        return service.setUsage(businessId, request, actor);
    }
}
