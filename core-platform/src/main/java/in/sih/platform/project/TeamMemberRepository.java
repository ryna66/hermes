package in.sih.platform.project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> { boolean existsByFacultyProfileId(UUID id); java.util.List<TeamMember> findByTeamId(UUID id);
}
