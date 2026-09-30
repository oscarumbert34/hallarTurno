package com.turnero.email.policy;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessEmailSubscriptionRepository extends JpaRepository<BusinessEmailSubscription, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BusinessEmailSubscription s where s.businessId = :id")
    Optional<BusinessEmailSubscription> findByIdForUpdate(@Param("id") UUID id);

    @Query("select s from BusinessEmailSubscription s where s.status = com.turnero.email.policy.SubscriptionStatus.TRIAL and s.trialEndsAt <= :now")
    List<BusinessEmailSubscription> findExpiredTrials(@Param("now") Instant now);
}
