package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.Venue; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface VenueRepository extends JpaRepository<Venue,Long>{ Optional<Venue> findByPublicId(UUID id); }
