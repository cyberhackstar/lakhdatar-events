package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash=:hash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt=:now where t.userId=:userId and t.usedAt is null")
    int invalidateUnusedByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff or t.usedAt is not null")
    int deleteExpiredOrUsed(@Param("cutoff") Instant cutoff);
}
