package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.User; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface UserRepository extends JpaRepository<User,Long>{ Optional<User> findByEmailIgnoreCase(String email); boolean existsByRole(com.neelastack.lakhdatar.domain.Enums.UserRole role); Optional<User> findByPublicId(UUID publicId); }
