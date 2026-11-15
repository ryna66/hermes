package in.sih.platform.config;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    @PrePersist protected void createTimestamp() { createdAt = Instant.now(); }
}
