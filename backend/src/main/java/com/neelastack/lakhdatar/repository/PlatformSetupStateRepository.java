package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.PlatformSetupState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformSetupStateRepository extends JpaRepository<PlatformSetupState, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlatformSetupState s where s.id = :id")
    Optional<PlatformSetupState> findByIdForUpdate(@Param("id") Short id);
}
