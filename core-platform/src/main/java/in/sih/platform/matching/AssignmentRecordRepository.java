package in.sih.platform.matching;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssignmentRecordRepository extends JpaRepository<AssignmentRecord, UUID> {
    Optional<AssignmentRecord> findFirstByChallengeIdOrderByCreatedAtDesc(UUID challengeId);
}
