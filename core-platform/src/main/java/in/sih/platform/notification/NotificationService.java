package in.sih.platform.notification;

import in.sih.platform.challenge.Challenge;
import in.sih.platform.organization.Organization;
import in.sih.platform.user.*;
import in.sih.platform.workflow.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final in.sih.platform.pledge.PledgeRepository pledges;
    public NotificationService(NotificationRepository notifications, UserRepository users, in.sih.platform.pledge.PledgeRepository pledges) {
        this.notifications = notifications; this.users = users; this.pledges = pledges;
    }
    public void notify(Challenge challenge, ChallengeEvent event, Organization previousUniversity, String message) {
        Map<UUID, User> recipients = new LinkedHashMap<>();
        recipients.put(challenge.getSubmittedBy().getId(), challenge.getSubmittedBy());
        users.findByRoleAndActiveTrue(Role.ADMIN).forEach(u -> recipients.put(u.getId(), u));
        for (Organization university : Arrays.asList(previousUniversity, challenge.getAssignedUniversity())) {
            if (university != null) users.findByOrganizationIdAndActiveTrue(university.getId())
                    .forEach(u -> recipients.put(u.getId(), u));
        }
        pledges.findByProjectChallengeId(challenge.getId()).stream()
                .forEach(p -> users.findByOrganizationIdAndActiveTrue(p.getIndustryOrganization().getId())
                        .forEach(u -> recipients.put(u.getId(), u)));
        recipients.values().forEach(user -> {
            Notification notification = new Notification();
            notification.setRecipient(user); notification.setChallenge(challenge);
            notification.setChallengeEvent(event); notification.setMessage(message);
            notifications.save(notification);
        });
        // Delivery occurs only after the database transaction succeeds.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                recipients.keySet().forEach(id -> log.info("SMS/email placeholder recipient={} challenge={} message={}",
                        id, challenge.getId(), message));
            }
        });
    }
}
