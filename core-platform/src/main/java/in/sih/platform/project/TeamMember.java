package in.sih.platform.project;
import in.sih.platform.config.MutableEntity;
import in.sih.platform.user.FacultyProfile;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "team_member", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "faculty_profile_id"}))
public class TeamMember extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "faculty_profile_id", nullable = false)
    private FacultyProfile facultyProfile;
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false)
    private TeamRole role;
    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }
    public FacultyProfile getFacultyProfile() { return facultyProfile; }
    public void setFacultyProfile(FacultyProfile facultyProfile) { this.facultyProfile = facultyProfile; }
    public TeamRole getRole() { return role; }
    public void setRole(TeamRole role) { this.role = role; }
}
