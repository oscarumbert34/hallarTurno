package com.turnero.email.policy;

import com.turnero.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@RestController
@ConditionalOnBean(name = "entityManagerFactory")
@RequestMapping("/api/v1/businesses/{businessId}/emails")
public class BusinessEmailController {
    private final BusinessEmailSettingsService service;
    public BusinessEmailController(BusinessEmailSettingsService service) { this.service = service; }

    @GetMapping
    EmailStatusResponse get(@PathVariable UUID businessId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.get(businessId, actor);
    }
    @PutMapping("/preferences")
    EmailStatusResponse preferences(@PathVariable UUID businessId, @Valid @RequestBody EmailPreferencesRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) { return service.updatePreferences(businessId, request, actor); }
    @PutMapping("/addon")
    EmailStatusResponse addon(@PathVariable UUID businessId, @Valid @RequestBody EmailAddonRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) { return service.selectAddon(businessId, request, actor); }
}
