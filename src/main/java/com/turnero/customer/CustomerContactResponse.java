package com.turnero.customer;

import java.util.UUID;

public record CustomerContactResponse(
        UUID id,
        UUID businessId,
        String name,
        String phone,
        String email
) {

    static CustomerContactResponse from(CustomerContact customerContact) {
        return new CustomerContactResponse(
                customerContact.getId(),
                customerContact.getBusiness().getId(),
                customerContact.getName(),
                customerContact.getPhone(),
                customerContact.getEmail()
        );
    }
}
