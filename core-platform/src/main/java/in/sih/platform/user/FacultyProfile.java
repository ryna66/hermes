package in.sih.platform.user;
import in.sih.platform.config.MutableEntity;
import in.sih.platform.organization.Organization;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "faculty_profile")
public class FacultyProfile extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    @OneToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "user_id", nullable = true, unique = true)
    private User user;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "department", nullable = false)
    private String department;
    @Column(name = "bio", nullable = false, columnDefinition = "text")
    private String bio;
    @Column(name = "email", nullable = false)
    private String email;
    @ElementCollection
    @CollectionTable(name = "faculty_expertise", joinColumns = @JoinColumn(name = "faculty_profile_id"))
    @Column(name = "tag", nullable = false)
    private Set<String> expertiseTags = new HashSet<>();
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Set<String> getExpertiseTags() { return expertiseTags; }
}
