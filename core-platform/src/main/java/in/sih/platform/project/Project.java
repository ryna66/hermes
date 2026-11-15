package in.sih.platform.project;
import in.sih.platform.challenge.Challenge;
import in.sih.platform.config.MutableEntity;
import in.sih.platform.organization.Organization;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "project")
public class Project extends MutableEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "challenge_id", nullable = false, unique = true)
    private Challenge challenge;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "university_id", nullable = false)
    private Organization university;
    @Column(name = "title", nullable = false)
    private String title;
    @Column(name = "proposal_text", nullable = false, columnDefinition = "text")
    private String proposalText;
    @Column(name = "impact_summary", nullable = true, columnDefinition = "text")
    private String impactSummary;
    @Version @Column(name = "version", nullable = false)
    private long version;
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public Organization getUniversity() { return university; }
    public void setUniversity(Organization university) { this.university = university; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getProposalText() { return proposalText; }
    public void setProposalText(String proposalText) { this.proposalText = proposalText; }
    public String getImpactSummary() { return impactSummary; }
    public void setImpactSummary(String impactSummary) { this.impactSummary = impactSummary; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
