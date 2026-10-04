package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.UserInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserInviteRepository extends JpaRepository<UserInvite, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserInvite i where i.tokenHash = :hash")
    Optional<UserInvite> findByTokenHashForUpdate(@Param("hash") String hash);

    boolean existsByUserIdAndUsedAtIsNullAndExpiresAtAfter(Long userId, Instant now);

    /** Closes every still-open invite of a user (a new invite was issued, or the user was deactivated). */
    @Modifying
    @Query("update UserInvite i set i.usedAt = :now where i.userId = :userId and i.usedAt is null")
    int closeOpenInvites(@Param("userId") Long userId, @Param("now") Instant now);
}
