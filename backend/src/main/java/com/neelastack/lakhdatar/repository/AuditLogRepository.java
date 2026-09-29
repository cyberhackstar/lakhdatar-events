package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.AuditLog; import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditLogRepository extends JpaRepository<AuditLog,Long>{}
