package in.sih.platform.workflow;
import in.sih.platform.challenge.Challenge;
import in.sih.platform.challenge.ChallengeStatus;
import in.sih.platform.config.BaseEntity;
import in.sih.platform.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "challenge_event")
public class ChallengeEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;
    @Enumerated(EnumType.STRING) @Column(name = "from_state", nullable = true)
    private ChallengeStatus fromState;
    @Enumerated(EnumType.STRING) @Column(name = "to_state", nullable = false)
    private ChallengeStatus toState;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id", nullable = false)
    private User actor;
    @Column(name = "reason", nullable = true, columnDefinition = "text")
    private String reason;
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public ChallengeStatus getFromState() { return fromState; }
    public void setFromState(ChallengeStatus fromState) { this.fromState = fromState; }
    public ChallengeStatus getToState() { return toState; }
    public void setToState(ChallengeStatus toState) { this.toState = toState; }
    public User getActor() { return actor; }
    public void setActor(User actor) { this.actor = actor; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
