package com.turnero.customer;

import com.turnero.auth.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CustomerContactController {

    private final CustomerContactService customerContactService;

    public CustomerContactController(CustomerContactService customerContactService) {
        this.customerContactService = customerContactService;
    }

    @GetMapping("/businesses/{businessId}/customer-contacts/search")
    CustomerContactResponse findByPhone(
            @PathVariable UUID businessId,
            @RequestParam @NotBlank String phone,
            @AuthenticationPrincipal
            AuthenticatedUser currentUser
    ) {
        return customerContactService.findByPhone(businessId, phone, currentUser);
    }
}
