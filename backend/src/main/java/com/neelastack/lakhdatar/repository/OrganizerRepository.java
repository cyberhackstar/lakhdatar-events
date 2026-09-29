package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Organizer; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface OrganizerRepository extends JpaRepository<Organizer,Long>{ Optional<Organizer> findByPublicId(UUID id); Optional<Organizer> findBySlug(String slug); Optional<Organizer> findFirstByOrderByIdAsc(); }
