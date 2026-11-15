package in.sih.platform.project;
import in.sih.platform.challenge.ChallengeService;
import in.sih.platform.user.User;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static in.sih.platform.project.CollaborationService.*;
@RestController
public class CollaborationController {
    private final CollaborationService service; private final ChallengeService challenges;
    public CollaborationController(CollaborationService service,ChallengeService challenges){this.service=service;this.challenges=challenges;}
    private User actor(Jwt jwt){return challenges.actor(UUID.fromString(jwt.getSubject()));}
    @PostMapping("/api/challenges/{id}/proposal-draft") @PreAuthorize("hasRole('UNIVERSITY')")
    public Draft draft(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return service.draft(id,actor(jwt));}
    @PostMapping("/api/challenges/{id}/project") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasRole('UNIVERSITY')")
    public View create(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Proposal input){return service.create(id,actor(jwt),input);}
    @GetMapping("/api/projects/{id}") @PreAuthorize("hasAnyRole('UNIVERSITY','ADMIN','GOVERNMENT','INDUSTRY')")
    public View read(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return service.read(id,actor(jwt));}
    @PatchMapping("/api/projects/{id}") @PreAuthorize("hasRole('UNIVERSITY')")
    public View edit(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Edit input){return service.edit(id,actor(jwt),input);}
    @PutMapping("/api/projects/{id}/team") @PreAuthorize("hasRole('UNIVERSITY')")
    public View team(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody TeamInput input){return service.team(id,actor(jwt),input);}
    @PostMapping("/api/projects/{id}/milestones") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasRole('UNIVERSITY')")
    public ProgressView add(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Progress input){return service.progress(id,null,actor(jwt),input);}
    @PatchMapping("/api/projects/{id}/milestones/{milestoneId}") @PreAuthorize("hasRole('UNIVERSITY')")
    public ProgressView progress(@PathVariable UUID id,@PathVariable UUID milestoneId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Progress input){return service.progress(id,milestoneId,actor(jwt),input);}
    @PostMapping("/api/projects/{id}/pledges") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @PreAuthorize("hasRole('INDUSTRY')")
    public SupportView pledge(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Support input){return service.pledge(id,actor(jwt),input);}
    @GetMapping({"/api/industry/projects","/api/industry/matched-problems"}) @PreAuthorize("hasRole('INDUSTRY')")
    public Page<View> matched(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.matched(actor(jwt),page,size);}
    @GetMapping("/api/industry/pledges") @PreAuthorize("hasRole('INDUSTRY')")
    public Page<SupportView> pledges(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.pledges(actor(jwt),page,size);}
}
