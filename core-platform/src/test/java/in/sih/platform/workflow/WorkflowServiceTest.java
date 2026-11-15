package in.sih.platform.workflow;

import in.sih.platform.challenge.*;
import in.sih.platform.notification.NotificationService;
import in.sih.platform.organization.*;
import in.sih.platform.project.*;
import in.sih.platform.pledge.*;
import in.sih.platform.user.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowServiceTest {
    ChallengeRepository challenges;
    ChallengeEventRepository events;
    NotificationService notifications;
    OrganizationRepository organizations;
    ProjectRepository projects;
    MilestoneRepository milestones;
    PledgeRepository pledges;
    WorkflowService service;
    Challenge challenge;
    User actor;
    Organization university;
    Project project;
    @BeforeEach void setup() {
        challenges = mock(ChallengeRepository.class); events = mock(ChallengeEventRepository.class);
        notifications = mock(NotificationService.class); organizations = mock(OrganizationRepository.class);
        projects = mock(ProjectRepository.class); milestones = mock(MilestoneRepository.class); pledges = mock(PledgeRepository.class);
        service = new WorkflowService(challenges, events, notifications, organizations, projects, milestones, pledges,
                Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC), Duration.ofDays(7));
        university = id(new Organization()); university.setType(OrganizationType.UNIVERSITY);
        actor = id(new User()); actor.setActive(true); actor.setOrganization(university);
        User citizen = id(new User()); citizen.setRole(Role.CITIZEN);
        challenge = id(new Challenge()); challenge.setSubmittedBy(citizen); challenge.setCategory(Category.HEALTH);
        challenge.setAssignedUniversity(university); challenge.setAssignmentResponse(AssignmentResponse.ACCEPTED);
        project = id(new Project()); project.setUniversity(university); project.setProposalText("Proposal");
        when(challenges.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(organizations.findById(university.getId())).thenReturn(Optional.of(university));
        when(projects.findByChallengeId(challenge.getId())).thenReturn(Optional.of(project));
    }
    static Stream<String[]> legal() {
        return Arrays.stream(TransitionPolicyTest.TABLE).flatMap(row -> Arrays.stream(row[2].split(","))
                .map(role -> new String[]{row[0], row[1], role}));
    }
    @ParameterizedTest @MethodSource("legal")
    void everyLegalTransitionWritesEventAndNotifies(String from, String to, String role) {
        challenge.setStatus(ChallengeStatus.valueOf(from)); actor.setRole(Role.valueOf(role));
        if (actor.getRole() == Role.INDUSTRY) {
            Organization industry = id(new Organization()); industry.setType(OrganizationType.INDUSTRY); actor.setOrganization(industry);
            Pledge pledge = new Pledge(); pledge.setCreatedBy(actor); pledge.setIndustryOrganization(industry);
            pledge.setSupportType(SupportType.MENTORSHIP); pledge.setDescription("Mentoring");
            when(pledges.findByProjectId(project.getId())).thenReturn(List.of(pledge));
        }
        Milestone milestone = new Milestone(); milestone.setStatus(MilestoneStatus.COMPLETED); milestone.setOutcome("Prototype evidence");
        when(milestones.findByProjectId(project.getId())).thenReturn(List.of(milestone));
        var command = new WorkflowService.Command(ChallengeStatus.valueOf(to), "Approved", Category.HEALTH, university.getId(), "Measured impact");
        service.transition(challenge.getId(), actor, command);
        assertEquals(ChallengeStatus.valueOf(to), challenge.getStatus());
        ArgumentCaptor<ChallengeEvent> event = ArgumentCaptor.forClass(ChallengeEvent.class);
        verify(events).save(event.capture());
        assertSame(challenge, event.getValue().getChallenge()); assertSame(actor, event.getValue().getActor());
        assertEquals(ChallengeStatus.valueOf(from), event.getValue().getFromState());
        assertEquals(ChallengeStatus.valueOf(to), event.getValue().getToState());
        verify(notifications).notify(eq(challenge), same(event.getValue()), eq(university), anyString());
        if (to.equals("ASSIGNED")) {
            assertEquals(AssignmentResponse.PENDING, challenge.getAssignmentResponse());
            assertEquals(Instant.parse("2026-11-22T00:00:00Z"), challenge.getResponseDeadline());
        }
        if (from.equals("ASSIGNED") && to.equals("CATEGORIZED")) {
            assertNull(challenge.getAssignedUniversity()); assertNull(challenge.getResponseDeadline()); assertNull(challenge.getAssignmentResponse());
        }
        if (to.equals("IMPACT_EVALUATED")) assertEquals("Measured impact", project.getImpactSummary());
    }
    @Test void failedGuardsDoNotWriteEventsOrNotifications() {
        actor.setRole(Role.ADMIN); challenge.setStatus(ChallengeStatus.SUBMITTED);
        assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor,
                new WorkflowService.Command(ChallengeStatus.REJECTED, " ", null, null, null)));
        challenge.setStatus(ChallengeStatus.VALIDATED); challenge.setCategory(null);
        assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor,
                new WorkflowService.Command(ChallengeStatus.CATEGORIZED, null, null, null, null)));
        verifyNoInteractions(events, notifications);
    }
    @Test void universityOwnershipAndAcceptanceAreRequired() {
        actor.setRole(Role.UNIVERSITY); challenge.setStatus(ChallengeStatus.ASSIGNED); actor.setOrganization(id(new Organization()));
        var command = new WorkflowService.Command(ChallengeStatus.PROPOSAL_CREATED, null, null, null, null);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor, command)).getStatusCode().value());
        actor.setOrganization(university); challenge.setAssignmentResponse(AssignmentResponse.PENDING);
        assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor, command));
        challenge.setAssignmentResponse(AssignmentResponse.ACCEPTED); when(projects.findByChallengeId(challenge.getId())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor, command));
        verifyNoInteractions(events, notifications);
    }
    @Test void pledgeEvidenceAndImpactGuardsCannotBeSkipped() {
        actor.setRole(Role.INDUSTRY); university.setType(OrganizationType.INDUSTRY); challenge.setStatus(ChallengeStatus.PROPOSAL_CREATED);
        assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor,
                new WorkflowService.Command(ChallengeStatus.INDUSTRY_CONNECTED, null, null, null, null)));
        actor.setRole(Role.UNIVERSITY); challenge.setStatus(ChallengeStatus.INDUSTRY_CONNECTED);
        assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor,
                new WorkflowService.Command(ChallengeStatus.PROTOTYPE, null, null, null, null)));
        actor.setRole(Role.GOVERNMENT); challenge.setStatus(ChallengeStatus.DEPLOYED);
        assertThrows(ResponseStatusException.class, () -> service.transition(challenge.getId(), actor,
                new WorkflowService.Command(ChallengeStatus.IMPACT_EVALUATED, null, null, null, " ")));
        verifyNoInteractions(events, notifications);
    }
    @Test void genericEndpointCannotCreateAssignmentOrProject() {
        actor.setRole(Role.ADMIN); challenge.setStatus(ChallengeStatus.CATEGORIZED);
        assertThrows(ResponseStatusException.class, () -> service.genericTransition(challenge.getId(), actor,
                new WorkflowService.Command(ChallengeStatus.ASSIGNED, null, null, university.getId(), null)));
        actor.setRole(Role.UNIVERSITY); challenge.setStatus(ChallengeStatus.ASSIGNED);
        assertThrows(ResponseStatusException.class, () -> service.genericTransition(challenge.getId(), actor,
                new WorkflowService.Command(ChallengeStatus.PROPOSAL_CREATED, null, null, null, null)));
        verifyNoInteractions(events, notifications);
    }
    private <T> T id(T entity) { ReflectionTestUtils.setField(entity, "id", UUID.randomUUID()); return entity; }
}
