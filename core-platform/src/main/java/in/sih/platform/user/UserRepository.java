package in.sih.platform.user;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="organization")
    java.util.Optional<User> findWithOrganizationById(UUID id);
    java.util.Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);
    java.util.List<User> findByRoleAndActiveTrue(Role role);
    java.util.List<User> findByOrganizationIdAndActiveTrue(UUID organizationId);
}
