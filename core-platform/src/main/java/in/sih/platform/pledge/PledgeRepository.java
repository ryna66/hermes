package in.sih.platform.pledge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PledgeRepository extends JpaRepository<Pledge, UUID> { }
