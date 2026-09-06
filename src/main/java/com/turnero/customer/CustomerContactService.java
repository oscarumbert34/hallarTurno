package com.turnero.customer;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerContactService {

    private final CustomerContactRepository customerContactRepository;
    private final BusinessRepository businessRepository;
    private final OwnershipGuard ownershipGuard;

    public CustomerContactService(
            CustomerContactRepository customerContactRepository,
            BusinessRepository businessRepository,
            OwnershipGuard ownershipGuard
    ) {
        this.customerContactRepository = customerContactRepository;
        this.businessRepository = businessRepository;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional(readOnly = true)
    public CustomerContactResponse findByPhone(UUID businessId, String phone, AuthenticatedUser currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Customer contacts can only be viewed by the business owner or an admin");
        String normalizedPhone = normalizeRequiredPhone(phone);
        return customerContactRepository.findByBusinessIdAndNormalizedPhone(businessId, normalizedPhone)
                .map(CustomerContactResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer contact not found"));
    }

    @Transactional
    public CustomerContact findOrCreateForBooking(Business business, String customerName, String customerPhone, String customerEmail) {
        String normalizedPhone = normalizeRequiredPhone(customerPhone);
        return customerContactRepository.findByBusinessIdAndNormalizedPhone(business.getId(), normalizedPhone)
                .map(existing -> {
                    existing.updateFromBooking(customerName, customerPhone, customerEmail);
                    return existing;
                })
                .orElseGet(() -> createContact(business, customerName, customerPhone, customerEmail, normalizedPhone));
    }

    private CustomerContact createContact(
            Business business,
            String customerName,
            String customerPhone,
            String customerEmail,
            String normalizedPhone
    ) {
        try {
            return customerContactRepository.saveAndFlush(
                    CustomerContact.create(business, customerName, customerPhone, customerEmail)
            );
        } catch (DataIntegrityViolationException exception) {
            return customerContactRepository.findByBusinessIdAndNormalizedPhone(business.getId(), normalizedPhone)
                    .map(existing -> {
                        existing.updateFromBooking(customerName, customerPhone, customerEmail);
                        return existing;
                    })
                    .orElseThrow(() -> exception);
        }
    }

    private String normalizeRequiredPhone(String phone) {
        String normalizedPhone = CustomerContact.normalizePhone(phone);
        if (normalizedPhone.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Phone is required");
        }
        return normalizedPhone;
    }
}
