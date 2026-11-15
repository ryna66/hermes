package in.sih.platform.user;
import in.sih.platform.config.MutableEntity;
import in.sih.platform.organization.Organization;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "app_user")
public class User extends MutableEntity {
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "phone", nullable = false, unique = true)
    private String phone;
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false)
    private Role role;
    @ManyToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "organization_id", nullable = true)
    private Organization organization;
    @Column(name = "active", nullable = false)
    private boolean active;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public boolean getActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
