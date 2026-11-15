package in.sih.platform.pledge;
import in.sih.platform.config.BaseEntity;
import in.sih.platform.organization.Organization;
import in.sih.platform.project.Project;
import in.sih.platform.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "pledge")
public class Pledge extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "industry_organization_id", nullable = false)
    private Organization industryOrganization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;
    @Enumerated(EnumType.STRING) @Column(name = "support_type", nullable = false)
    private SupportType supportType;
    @Column(name = "amount", nullable = true, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "currency", nullable = true)
    private String currency;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public Organization getIndustryOrganization() { return industryOrganization; }
    public void setIndustryOrganization(Organization industryOrganization) { this.industryOrganization = industryOrganization; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public SupportType getSupportType() { return supportType; }
    public void setSupportType(SupportType supportType) { this.supportType = supportType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
