package com.turnero.business;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessConfigurationRepository extends JpaRepository<BusinessConfiguration, UUID> {
}
