package in.sih.platform.matching;
import in.sih.platform.challenge.*;
import in.sih.platform.notification.NotificationService;
import java.time.Clock;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@EnableScheduling
public class AssignmentDeadlineJob {
    private final ChallengeRepository challenges;
    private final AssignmentRecordRepository assignments;
    private final NotificationService notifications;
    private final Clock clock;
    public AssignmentDeadlineJob(ChallengeRepository challenges, AssignmentRecordRepository assignments, NotificationService notifications, Clock clock) {
        this.challenges=challenges; this.assignments=assignments; this.notifications=notifications; this.clock=clock;
    }
    @Scheduled(fixedDelayString="${workflow.deadline-check-interval:60000}")
    @Transactional
    public void flagOverdue() {
        challenges.findAll().stream().filter(c -> c.getStatus()==ChallengeStatus.ASSIGNED &&
                c.getAssignmentResponse()==AssignmentResponse.PENDING && !c.getReassignmentRequired() &&
                c.getResponseDeadline()!=null && !clock.instant().isBefore(c.getResponseDeadline())).forEach(c -> {
            c.setReassignmentRequired(true);
            assignments.findFirstByChallengeIdOrderByCreatedAtDesc(c.getId()).ifPresent(a -> a.setResponse("OVERDUE"));
            notifications.notify(c,null,null,"Assignment overdue; admin reassignment required");
        });
    }
}
