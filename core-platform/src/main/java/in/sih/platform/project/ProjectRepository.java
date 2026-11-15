package in.sih.platform.project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, UUID> {     java.util.Optional<Project> findByChallengeId(UUID challengeId);
}
