package in.sih.platform.workflow;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChallengeEventRepository extends JpaRepository<ChallengeEvent, UUID> {     java.util.List<ChallengeEvent> findByChallengeIdOrderByCreatedAtAsc(UUID challengeId);
}
