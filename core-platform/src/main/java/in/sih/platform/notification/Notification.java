package in.sih.platform.notification;
import in.sih.platform.challenge.Challenge;
import in.sih.platform.config.MutableEntity;
import in.sih.platform.user.User;
import in.sih.platform.workflow.ChallengeEvent;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "notification")
public class Notification extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;
    @ManyToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "challenge_id", nullable = true)
    private Challenge challenge;
    @ManyToOne(fetch = FetchType.LAZY, optional = true) @JoinColumn(name = "challenge_event_id", nullable = true)
    private ChallengeEvent challengeEvent;
    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;
    @Column(name = "read_at", nullable = true)
    private Instant readAt;
    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }
    public Challenge getChallenge() { return challenge; }
    public void setChallenge(Challenge challenge) { this.challenge = challenge; }
    public ChallengeEvent getChallengeEvent() { return challengeEvent; }
    public void setChallengeEvent(ChallengeEvent challengeEvent) { this.challengeEvent = challengeEvent; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
