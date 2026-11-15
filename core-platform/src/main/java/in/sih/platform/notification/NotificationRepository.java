package in.sih.platform.notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<Notification, UUID> { org.springframework.data.domain.Page<Notification> findByRecipientId(UUID id, org.springframework.data.domain.Pageable pageable);
}
