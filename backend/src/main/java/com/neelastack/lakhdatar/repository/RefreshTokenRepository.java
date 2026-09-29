package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long>{
    Optional<RefreshToken> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash=:hash")
    Optional<RefreshToken> findByTokenHashForUpdate(String hash);
    @Modifying
    @Query("update RefreshToken r set r.revokedAt=:now where r.userId=:userId and r.revokedAt is null")
    int revokeAllActiveByUserId(Long userId, Instant now);
    @Transactional
    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff or (r.revokedAt is not null and r.createdAt < :cutoff)")
    int deleteExpiredOrOldRevoked(Instant cutoff);
}
