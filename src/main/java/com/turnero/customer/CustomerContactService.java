package com.turnero.customer;

import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.business.BusinessStatus;
import com.turnero.common.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerContactService {

    private final CustomerContactRepository customerContactRepository;
    private final BusinessRepository businessRepository;

    public CustomerContactService(
            CustomerContactRepository customerContactRepository,
            BusinessRepository businessRepository
    ) {
        this.customerContactRepository = customerContactRepository;
        this.businessRepository = businessRepository;
    }

    @Transactional(readOnly = true)
    public CustomerEmailStatusResponse findEmailStatus(UUID businessId, String phone) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        if (business.getStatus() != BusinessStatus.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Business not found");
        }
        String normalizedPhone = normalizeRequiredPhone(phone);
        boolean emailRequired = customerContactRepository
                .findByBusinessIdAndNormalizedPhone(businessId, normalizedPhone)
                .map(contact -> contact.getEmail() == null || contact.getEmail().isBlank())
                .orElse(true);
        return new CustomerEmailStatusResponse(emailRequired);
    }

    @Transactional
    public CustomerContact findOrCreateForBooking(Business business, String customerName, String customerPhone, String customerEmail) {
        String normalizedPhone = normalizeRequiredPhone(customerPhone);
        Optional<CustomerContact> existingContact = customerContactRepository
                .findByBusinessIdAndNormalizedPhone(business.getId(), normalizedPhone);
        return existingContact
                .map(existing -> {
                    existing.updateFromBooking(customerName, customerPhone, customerEmail);
                    return existing;
                })
                .orElseGet(() -> createContact(business, customerName, customerPhone, customerEmail, normalizedPhone));
    }

    @Transactional(readOnly = true)
    public void requireEmailForPublicBooking(Business business, String phone, String email) {
        if (email != null && !email.isBlank()) {
            return;
        }
        String normalizedPhone = normalizeRequiredPhone(phone);
        boolean hasStoredEmail = customerContactRepository
                .findByBusinessIdAndNormalizedPhone(business.getId(), normalizedPhone)
                .map(CustomerContact::getEmail)
                .filter(storedEmail -> !storedEmail.isBlank())
                .isPresent();
        if (!hasStoredEmail) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer email is required for the first booking");
        }
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
