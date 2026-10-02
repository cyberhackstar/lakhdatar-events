package com.neelastack.lakhdatar.repository;
import com.neelastack.lakhdatar.domain.OrganizerMember; import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.List;
public interface OrganizerMemberRepository extends JpaRepository<OrganizerMember,Long>{
 Optional<OrganizerMember> findByOrganizerIdAndUserId(Long organizerId,Long userId);
 boolean existsByOrganizerIdAndUserId(Long organizerId,Long userId);
 List<OrganizerMember> findByUserId(Long userId);
}
