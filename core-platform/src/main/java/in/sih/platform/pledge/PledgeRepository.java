package in.sih.platform.pledge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PledgeRepository extends JpaRepository<Pledge, UUID> {
    java.util.List<Pledge> findByProjectChallengeId(UUID challengeId);     java.util.List<Pledge> findByProjectId(UUID projectId);
org.springframework.data.domain.Page<Pledge> findByIndustryOrganizationId(UUID id, org.springframework.data.domain.Pageable pageable);
}
