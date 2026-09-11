package com.turnero.booking;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingActionTokenRepository extends JpaRepository<BookingActionToken, UUID> {

    @EntityGraph(attributePaths = {"booking", "booking.business", "booking.branch"})
    Optional<BookingActionToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from BookingActionToken token join fetch token.booking booking " +
            "join fetch booking.business join fetch booking.branch where token.tokenHash = :tokenHash")
    Optional<BookingActionToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
