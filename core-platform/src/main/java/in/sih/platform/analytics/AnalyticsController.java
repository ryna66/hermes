package in.sih.platform.analytics;
import in.sih.platform.challenge.ChallengeService;
import in.sih.platform.user.Role;
import in.sih.platform.workflow.WorkflowService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
public class AnalyticsController {
    private final AnalyticsService analytics;private final ChallengeService challenges;
    public AnalyticsController(AnalyticsService analytics,ChallengeService challenges){this.analytics=analytics;this.challenges=challenges;}
    @GetMapping("/api/government/analytics") @PreAuthorize("hasRole('GOVERNMENT')")
    public AnalyticsService.Dashboard dashboard(@AuthenticationPrincipal Jwt jwt){WorkflowService.requireRole(challenges.actor(UUID.fromString(jwt.getSubject())),Role.GOVERNMENT);return analytics.dashboard();}
}
