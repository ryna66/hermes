package in.sih.platform.notification;
import in.sih.platform.challenge.ChallengeService;
import static in.sih.platform.project.CollaborationService.paging;
import java.util.UUID;
import java.time.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@Transactional
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationRepository notifications; private final ChallengeService challenges; private final Clock clock;
    public NotificationController(NotificationRepository notifications,ChallengeService challenges,Clock clock){this.notifications=notifications;this.challenges=challenges;this.clock=clock;}
    public record View(UUID id,UUID challengeId,String message,Instant createdAt,Instant readAt){
        static View of(Notification n){return new View(n.getId(),n.getChallenge()==null?null:n.getChallenge().getId(),n.getMessage(),n.getCreatedAt(),n.getReadAt());}
    }
    private UUID actor(Jwt jwt){return challenges.actor(UUID.fromString(jwt.getSubject())).getId();}
    @GetMapping("/api/notifications")
    public Page<View> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return notifications.findByRecipientId(actor(jwt),paging(page,size)).map(View::of);}
    @PatchMapping("/api/notifications/{id}/read")
    public View read(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
        UUID user=actor(jwt);Notification n=notifications.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found"));
        if(!n.getRecipient().getId().equals(user))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only recipient may mark notification read");
        if(n.getReadAt()==null)n.setReadAt(clock.instant());return View.of(n);
    }
}
