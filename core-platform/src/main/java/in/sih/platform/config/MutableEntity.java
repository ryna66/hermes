package in.sih.platform.config;
import jakarta.persistence.*;
import java.time.Instant;
@MappedSuperclass
public abstract class MutableEntity extends BaseEntity {
    @Column(nullable = false) private Instant updatedAt;
    public Instant getUpdatedAt() { return updatedAt; }
    @PrePersist @PreUpdate protected void updateTimestamp() { updatedAt = Instant.now(); }
}
