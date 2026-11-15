package in.sih.platform.matching;

import in.sih.platform.challenge.*;
import in.sih.platform.workflow.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/challenges")
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class AdminMatchingController {
    private final ChallengeRepository challenges;
    private final ChallengeService service;
    private final WorkflowService workflow;
    private final DuplicateService duplicates;
    private final MatchingService matching;
    public AdminMatchingController(ChallengeRepository challenges, ChallengeService service, WorkflowService workflow, DuplicateService duplicates, MatchingService matching) {
        this.challenges=challenges; this.service=service; this.workflow=workflow; this.duplicates=duplicates; this.matching=matching;
    }
    public record Review(ChallengeService.View challenge, Category suggestedCategory, Double confidence, boolean moderationRequired,
                         String moderationReason, boolean duplicateCandidate, UUID duplicateOfId, UUID assignedUniversityId,
                         java.time.Instant responseDeadline, AssignmentResponse assignmentResponse, boolean reassignmentRequired) {
        static Review of(Challenge c) { return new Review(ChallengeService.View.of(c),c.getSuggestedCategory(),c.getClassificationConfidence(),c.getModerationRequired(),c.getModerationReason(),c.getDuplicateCandidate(),c.getDuplicateOf()==null?null:c.getDuplicateOf().getId(),c.getAssignedUniversity()==null?null:c.getAssignedUniversity().getId(),c.getResponseDeadline(),c.getAssignmentResponse(),c.getReassignmentRequired()); }
    }
    @GetMapping
    public Page<Review> queue(@RequestParam(defaultValue="moderation") String queue, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        WorkflowService.require(page>=0 && size>=1 && size<=100,"Invalid pagination");
        WorkflowService.require(Set.of("moderation","allocation","overdue").contains(queue),"Unknown queue");
        return challenges.findAll((root,q,cb) -> cb.and(cb.isNull(root.get("duplicateOf")), switch(queue) {
            case "allocation" -> cb.and(cb.equal(root.get("status"),ChallengeStatus.CATEGORIZED),cb.isNull(root.get("duplicateOf")));
            case "overdue" -> cb.isTrue(root.get("reassignmentRequired"));
            default -> cb.or(cb.isTrue(root.get("moderationRequired")), cb.isTrue(root.get("duplicateCandidate")), cb.equal(root.get("status"),ChallengeStatus.SUBMITTED), cb.equal(root.get("status"),ChallengeStatus.VALIDATED));
        }),PageRequest.of(page,size,Sort.by("createdAt").and(Sort.by("id")))).map(Review::of);
    }
    @GetMapping("/{id}/duplicates") public List<DuplicateService.Candidate> duplicates(@PathVariable UUID id) { return duplicates.candidates(workflow.requireChallenge(id)); }
    @GetMapping("/{id}/university-matches") public List<MatchingService.UniversityMatch> matches(@PathVariable UUID id) { return matching.rank(workflow.requireChallenge(id)); }
    public record Merge(@NotNull UUID originalId) { }
    @PostMapping("/{id}/merge") public Review merge(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Merge input) {
        return Review.of(duplicates.merge(workflow.requireChallenge(id),workflow.requireChallenge(input.originalId()),service.actor(UUID.fromString(jwt.getSubject()))));
    }
    public record Classification(@NotNull Category category) { }
    @PatchMapping("/{id}/classification") public Review classify(@PathVariable UUID id, @Valid @RequestBody Classification input) {
        Challenge c=workflow.requireChallenge(id);
        WorkflowService.require(c.getStatus()==ChallengeStatus.SUBMITTED || c.getStatus()==ChallengeStatus.VALIDATED || c.getStatus()==ChallengeStatus.CATEGORIZED,"Classification requires an unallocated challenge");
        c.setCategory(input.category()); c.setModerationRequired(false); c.setModerationReason(null); return Review.of(c);
    }
}
