package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.MfaChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MfaChallenge c where c.tokenHash=:hash")
    Optional<MfaChallenge> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update MfaChallenge c set c.usedAt=:now where c.userId=:userId and c.usedAt is null")
    int invalidateActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from MfaChallenge c where c.expiresAt < :cutoff or c.usedAt is not null")
    int deleteExpiredOrUsed(@Param("cutoff") Instant cutoff);
}
