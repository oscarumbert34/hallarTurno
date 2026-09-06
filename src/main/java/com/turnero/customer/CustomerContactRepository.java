package com.turnero.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerContactRepository extends JpaRepository<CustomerContact, UUID> {

    Optional<CustomerContact> findByBusinessIdAndNormalizedPhone(UUID businessId, String normalizedPhone);
}
