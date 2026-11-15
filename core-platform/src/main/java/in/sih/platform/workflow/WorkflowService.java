package in.sih.platform.workflow;

import in.sih.platform.challenge.*;
import in.sih.platform.notification.NotificationService;
import in.sih.platform.organization.*;
import in.sih.platform.project.*;
import in.sih.platform.pledge.*;
import in.sih.platform.user.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WorkflowService {
    private final ChallengeRepository challenges;
    private final ChallengeEventRepository events;
    private final NotificationService notifications;
    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final PledgeRepository pledges;
    private final Clock clock;
    private final Duration responseDeadline;
    private final in.sih.platform.matching.AssignmentRecordRepository assignments;
    public WorkflowService(ChallengeRepository challenges, ChallengeEventRepository events,
            NotificationService notifications, OrganizationRepository organizations, ProjectRepository projects,
            MilestoneRepository milestones, PledgeRepository pledges, Clock clock,
            @Value("${workflow.university-response-deadline:P7D}") Duration responseDeadline, in.sih.platform.matching.AssignmentRecordRepository assignments) {
        this.challenges = challenges; this.events = events; this.notifications = notifications;
        this.organizations = organizations; this.projects = projects; this.milestones = milestones;
        this.pledges = pledges; this.clock = clock; this.responseDeadline = responseDeadline; this.assignments = assignments;
    }
    public record Command(ChallengeStatus toState, String reason, Category category, UUID universityId, String impactSummary) { }
    public Challenge requireChallenge(UUID id) {
        return challenges.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
    }
    public void submitted(Challenge challenge, User actor) {
        requireRole(actor, Role.CITIZEN);
        record(challenge, null, actor, null, null);
    }
    /** Used by future project and pledge services within their resource-creation transaction. */
    public Challenge transition(UUID id, User actor, Command command) {
        Challenge c = requireChallenge(id);
        TransitionPolicy.check(c.getStatus(), command.toState(), actor.getRole());
        if (!actor.getActive()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        if (actor.getRole() == Role.UNIVERSITY) requireUniversity(c, actor);
        var from = c.getStatus();
        var previousUniversity = c.getAssignedUniversity();
        switch (command.toState()) {
            case REJECTED, PILOT, DEPLOYED -> requireText(command.reason(), "Reason required");
            case CATEGORIZED -> {
                if (from == ChallengeStatus.ASSIGNED) {
                    assignments.findFirstByChallengeIdOrderByCreatedAtDesc(id).ifPresent(a -> {
                        if (a.getResponse().equals("PENDING")) a.setResponse(actor.getRole()==Role.UNIVERSITY ? "DECLINED" : "RECALLED");
                    });
                    c.setReassignmentRequired(false);
                    c.setAssignedUniversity(null); c.setAssignedAt(null); c.setResponseDeadline(null); c.setAssignmentResponse(null);
                } else {
                    if (command.category() != null) c.setCategory(command.category());
                    require(c.getCategory() != null, "Category required");
                    c.setModerationRequired(false); c.setModerationReason(null);
                }
            }
            case ASSIGNED -> {
                require(command.universityId() != null, "University required");
                Organization university = organizations.findById(command.universityId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "University not found"));
                require(university.getType() == OrganizationType.UNIVERSITY, "Selected organization must be a university");
                require(c.getDuplicateOf() == null, "Merged duplicates cannot be allocated");
                assignments.save(new in.sih.platform.matching.AssignmentRecord(id, university.getId()));
                c.setReassignmentRequired(false);
                c.setAssignedUniversity(university); c.setAssignedAt(clock.instant());
                c.setResponseDeadline(clock.instant().plus(responseDeadline)); c.setAssignmentResponse(AssignmentResponse.PENDING);
            }
            case PROPOSAL_CREATED -> {
                require(c.getAssignmentResponse() == AssignmentResponse.ACCEPTED, "University must accept the assignment first");
                Project project = project(c);
                require(project.getUniversity().getId().equals(c.getAssignedUniversity().getId()), "Project university must match assignment");
                requireText(project.getProposalText(), "Nonempty project proposal required");
            }
            case INDUSTRY_CONNECTED -> {
                require(actor.getOrganization() != null && actor.getOrganization().getType() == OrganizationType.INDUSTRY,
                        "Industry organization required");
                boolean valid = pledges.findByProjectId(project(c).getId()).stream().anyMatch(p ->
                        p.getCreatedBy().getId().equals(actor.getId()) &&
                        p.getIndustryOrganization().getId().equals(actor.getOrganization().getId()) &&
                        p.getSupportType() != null && p.getDescription() != null && !p.getDescription().isBlank() &&
                        (p.getSupportType() != SupportType.FUNDING || (p.getAmount() != null && p.getAmount().signum() > 0 &&
                                p.getCurrency() != null && !p.getCurrency().isBlank())));
                require(valid, "A valid pledge from the acting industry user is required");
            }
            case PROTOTYPE -> require(milestones.findByProjectId(project(c).getId()).stream()
                    .anyMatch(m -> m.getStatus() == MilestoneStatus.COMPLETED && m.getOutcome() != null && !m.getOutcome().isBlank()),
                    "Completed milestone with prototype evidence required");
            case IMPACT_EVALUATED -> {
                requireText(command.impactSummary(), "Impact summary required");
                project(c).setImpactSummary(command.impactSummary());
            }
            default -> { }
        }
        c.setStatus(command.toState());
        record(c, from, actor, command.reason(), previousUniversity);
        return c;
    }
    public Challenge genericTransition(UUID id, User actor, Command command) {
        // Validate role/state first; resource-specific transitions cannot be forged via this endpoint.
        Challenge c = requireChallenge(id);
        TransitionPolicy.check(c.getStatus(), command.toState(), actor.getRole());
        require(!Set.of(ChallengeStatus.ASSIGNED, ChallengeStatus.PROPOSAL_CREATED, ChallengeStatus.INDUSTRY_CONNECTED)
                .contains(command.toState()), "Use the assignment, project or pledge action for this transition");
        return transition(id, actor, command);
    }
    public Challenge assign(UUID id, User actor, UUID universityId) {
        return transition(id, actor, new Command(ChallengeStatus.ASSIGNED, null, null, universityId, null));
    }
    public Challenge respond(UUID id, User actor, AssignmentResponse response, String reason) {
        requireRole(actor, Role.UNIVERSITY);
        Challenge c = requireChallenge(id); requireUniversity(c, actor);
        if (c.getStatus() != ChallengeStatus.ASSIGNED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Challenge is not ASSIGNED");
        if (c.getAssignmentResponse() != AssignmentResponse.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment already answered");
        if (c.getResponseDeadline() != null && !clock.instant().isBefore(c.getResponseDeadline()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment deadline has passed; admin reassignment required");
        require(response == AssignmentResponse.ACCEPTED || response == AssignmentResponse.DECLINED, "Accept or decline required");
        if (response == AssignmentResponse.DECLINED)
            return transition(id, actor, new Command(ChallengeStatus.CATEGORIZED, reason, null, null, null));
        c.setAssignmentResponse(response);
        assignments.findFirstByChallengeIdOrderByCreatedAtDesc(id).ifPresent(a -> a.setResponse("ACCEPTED"));
        return c;
    }
    public Challenge confirmFixed(UUID id, User actor) {
        requireRole(actor, Role.CITIZEN);
        Challenge c = requireChallenge(id);
        if (!c.getSubmittedBy().getId().equals(actor.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the submitter may confirm fixed");
        if (c.getStatus() != ChallengeStatus.DEPLOYED && c.getStatus() != ChallengeStatus.IMPACT_EVALUATED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirm fixed requires DEPLOYED or IMPACT_EVALUATED");
        if (c.getFixedConfirmedAt() == null) {
            c.setFixedConfirmedAt(clock.instant());
            record(c, c.getStatus(), actor, "Citizen confirmed fixed", null);
        }
        return c;
    }
    private Project project(Challenge c) {
        return projects.findByChallengeId(c.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Persisted project required"));
    }
    private void record(Challenge c, ChallengeStatus from, User actor, String reason, Organization previousUniversity) {
        ChallengeEvent event = new ChallengeEvent(); event.setChallenge(c); event.setFromState(from);
        event.setToState(c.getStatus()); event.setActor(actor); event.setReason(reason); events.save(event);
        notifications.notify(c, event, previousUniversity, from == c.getStatus()
                ? "Citizen confirmed fixed" : "Challenge status: " + c.getStatus());
    }
    public static void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    public static void requireText(String text, String message) { require(text != null && !text.isBlank(), message); }
    public static void requireRole(User actor, Role role) {
        if (!actor.getActive() || actor.getRole() != role) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role " + role + " required");
    }
    private void requireUniversity(Challenge c, User actor) {
        if (actor.getOrganization() == null || c.getAssignedUniversity() == null ||
                !actor.getOrganization().getId().equals(c.getAssignedUniversity().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned university may act");
    }
}
