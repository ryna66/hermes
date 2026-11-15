package in.sih.platform.matching;
import in.sih.platform.config.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name="assignment_record")
public class AssignmentRecord extends BaseEntity {
    @Column(name="challenge_id", nullable=false) private UUID challengeId;
    @Column(name="university_id", nullable=false) private UUID universityId;
    @Column(nullable=false) private String response;
    protected AssignmentRecord() { }
    public AssignmentRecord(UUID challenge, UUID university) { challengeId=challenge; universityId=university; response="PENDING"; }
    public UUID getUniversityId() { return universityId; }
    public String getResponse() { return response; }
    public void setResponse(String value) { response=value; }
}
