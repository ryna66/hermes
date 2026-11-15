package in.sih.platform.challenge;

import in.sih.platform.workflow.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ChallengeController {
    private final ChallengeService challenges;
    private final WorkflowService workflow;
    public ChallengeController(ChallengeService challenges, WorkflowService workflow) {
        this.challenges = challenges; this.workflow = workflow;
    }
    @PostMapping(value = "/api/challenges", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CITIZEN')")
    public ChallengeService.View submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChallengeInput input) {
        return challenges.submit(UUID.fromString(jwt.getSubject()), input, null, null);
    }
    @PostMapping(value = "/api/challenges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CITIZEN')")
    public ChallengeService.View upload(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("challenge") ChallengeInput input,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "audio", required = false) MultipartFile audio) {
        return challenges.submit(UUID.fromString(jwt.getSubject()), input, photo, audio);
    }
    @GetMapping("/api/challenges")
    public Page<ChallengeService.View> explore(@RequestParam(required = false) String district,
            @RequestParam(required = false) Category category, @RequestParam(required = false) ChallengeStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return challenges.explore(district, category, status, page, size);
    }
    @GetMapping("/api/challenges/{id}")
    public ChallengeService.View detail(@PathVariable UUID id) { return challenges.detail(id); }
    public record Transition(@NotNull ChallengeStatus toState, String reason, Category category, String impactSummary) { }
    @PostMapping("/api/challenges/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADMIN','UNIVERSITY','GOVERNMENT')")
    public ChallengeService.View transition(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Transition input) {
        return ChallengeService.View.of(workflow.genericTransition(id, challenges.actor(UUID.fromString(jwt.getSubject())),
                new WorkflowService.Command(input.toState(), input.reason(), input.category(), null, input.impactSummary())));
    }
    public record Assignment(@NotNull UUID universityId) { }
    @PostMapping("/api/admin/challenges/{id}/assignment") @PreAuthorize("hasRole('ADMIN')")
    public ChallengeService.View assign(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Assignment input) {
        return ChallengeService.View.of(workflow.assign(id, challenges.actor(UUID.fromString(jwt.getSubject())), input.universityId()));
    }
    public record Response(@NotNull AssignmentResponse response, String reason) { }
    @PostMapping("/api/challenges/{id}/assignment-response") @PreAuthorize("hasRole('UNIVERSITY')")
    public ChallengeService.View respond(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Response input) {
        return ChallengeService.View.of(workflow.respond(id, challenges.actor(UUID.fromString(jwt.getSubject())), input.response(), input.reason()));
    }
    @PostMapping("/api/challenges/{id}/confirm-fixed") @PreAuthorize("hasRole('CITIZEN')")
    public ChallengeService.View confirmFixed(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ChallengeService.View.of(workflow.confirmFixed(id, challenges.actor(UUID.fromString(jwt.getSubject()))));
    }
}
