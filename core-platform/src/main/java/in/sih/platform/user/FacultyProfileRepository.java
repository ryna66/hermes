package in.sih.platform.user;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FacultyProfileRepository extends JpaRepository<FacultyProfile, UUID> { }
