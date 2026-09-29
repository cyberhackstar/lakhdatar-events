package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.BrandConfiguration; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface BrandConfigurationRepository extends JpaRepository<BrandConfiguration,Long>{ Optional<BrandConfiguration> findByScopeAndOrganizerId(String scope,Long organizerId); }
