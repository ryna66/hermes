package in.sih.platform.challenge;
import in.sih.platform.config.MutableEntity;
import in.sih.platform.organization.Organization;
import in.sih.platform.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "challenge")
public class Challenge extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;
    @Column(name = "title", nullable = false)
    private String title;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "latitude", nullable = false)
    private double latitude;
    @Column(name = "longitude", nullable = false)
    private double longitude;
    @Enumerated(EnumType.STRING) @Column(name = "category", nullable = true)
    private Category category;
    @Enumerated(EnumType.STRING) @Column(name = "suggested_category", nullable = true)
    private Category suggestedCategory;
    @Column(name = "classification_confidence", nullable = true)
    private Double classificationConfidence;
    @Column(name = "embedding_text", nullable = true, columnDefinition = "text")
    private String embeddingText;
    @Column(name = "embedding_model", nullable = true)
    private String embeddingModel;
    @ManyToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "duplicate_of_id", nullable = true)
    private Challenge duplicateOf;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private ChallengeStatus status;
    @Column(name = "moderation_required", nullable = false)
    private boolean moderationRequired;
    @Column(name = "moderation_reason", nullable = true, columnDefinition = "text")
    private String moderationReason;
    @ManyToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "assigned_university_id", nullable = true)
    private Organization assignedUniversity;
    @Column(name = "assigned_at", nullable = true)
    private Instant assignedAt;
    @Column(name = "response_deadline", nullable = true)
    private Instant responseDeadline;
    @Enumerated(EnumType.STRING) @Column(name = "assignment_response", nullable = true)
    private AssignmentResponse assignmentResponse;
    @Version @Column(name = "version", nullable = false)
    private long version;
    public User getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(User submittedBy) { this.submittedBy = submittedBy; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Category getSuggestedCategory() { return suggestedCategory; }
    public void setSuggestedCategory(Category suggestedCategory) { this.suggestedCategory = suggestedCategory; }
    public Double getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(Double classificationConfidence) { this.classificationConfidence = classificationConfidence; }
    public String getEmbeddingText() { return embeddingText; }
    public void setEmbeddingText(String embeddingText) { this.embeddingText = embeddingText; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Challenge getDuplicateOf() { return duplicateOf; }
    public void setDuplicateOf(Challenge duplicateOf) { this.duplicateOf = duplicateOf; }
    public ChallengeStatus getStatus() { return status; }
    public void setStatus(ChallengeStatus status) { this.status = status; }
    public boolean getModerationRequired() { return moderationRequired; }
    public void setModerationRequired(boolean moderationRequired) { this.moderationRequired = moderationRequired; }
    public String getModerationReason() { return moderationReason; }
    public void setModerationReason(String moderationReason) { this.moderationReason = moderationReason; }
    public Organization getAssignedUniversity() { return assignedUniversity; }
    public void setAssignedUniversity(Organization assignedUniversity) { this.assignedUniversity = assignedUniversity; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public Instant getResponseDeadline() { return responseDeadline; }
    public void setResponseDeadline(Instant responseDeadline) { this.responseDeadline = responseDeadline; }
    public AssignmentResponse getAssignmentResponse() { return assignmentResponse; }
    public void setAssignmentResponse(AssignmentResponse assignmentResponse) { this.assignmentResponse = assignmentResponse; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
