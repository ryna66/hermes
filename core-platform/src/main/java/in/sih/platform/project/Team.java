package in.sih.platform.project;
import in.sih.platform.config.MutableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "team")
public class Team extends MutableEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;
    @Column(name = "name", nullable = false)
    private String name;
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
