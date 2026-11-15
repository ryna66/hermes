package in.sih.platform.project;

import in.sih.platform.ai.*;
import in.sih.platform.challenge.*;
import in.sih.platform.notification.*;
import in.sih.platform.pledge.*;
import in.sih.platform.user.*;
import in.sih.platform.organization.OrganizationType;
import in.sih.platform.workflow.WorkflowService;
import static in.sih.platform.workflow.WorkflowService.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CollaborationService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final ProjectRepository projects;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final MilestoneRepository milestones;
    private final FacultyProfileRepository faculty;
    private final PledgeRepository pledges;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final AiClient ai;
    private final Clock clock;
    private final double threshold;
    public CollaborationService(ProjectRepository projects, TeamRepository teams, TeamMemberRepository members,
            MilestoneRepository milestones, FacultyProfileRepository faculty, PledgeRepository pledges,
            WorkflowService workflow, NotificationService notifications, AiClient ai, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${ai.confidence-threshold:0.70}") double threshold) {
        this.projects=projects; this.teams=teams; this.members=members; this.milestones=milestones; this.faculty=faculty;
        this.pledges=pledges; this.workflow=workflow; this.notifications=notifications; this.ai=ai; this.clock=clock; this.threshold=threshold;
    }
    public record Proposal(@NotBlank @Size(max=255) String title, @NotBlank String proposalText, @NotBlank @Size(max=255) String teamName) {}
    public record Edit(String proposalText, String impactSummary) {}
    public record Member(@NotNull UUID facultyProfileId, @NotNull TeamRole role) {}
    public record TeamInput(@NotBlank @Size(max=255) String name, @NotNull List<@Valid Member> members) {}
    public record Progress(@NotBlank @Size(max=255) String title, @NotBlank String description, @NotNull LocalDate dueDate,
                           @NotNull MilestoneStatus status, String outcome) {}
    public record Support(@NotNull SupportType supportType, @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,
                          @Pattern(regexp="[A-Z]{3}") String currency, @NotBlank String description) {}
    public record Draft(String proposal, boolean draft, String aiStatus) {}
    public record MemberView(UUID id, UUID facultyProfileId, String name, TeamRole role) {}
    public record TeamView(UUID id, String name, List<MemberView> members) {}
    public record ProgressView(UUID id, String title, String description, LocalDate dueDate, MilestoneStatus status, Instant completedAt, String outcome) {}
    public record SupportView(UUID id, UUID projectId, UUID industryOrganizationId, SupportType supportType, BigDecimal amount, String currency, String description) {}
    public record View(UUID id, UUID challengeId, UUID universityId, String title, String proposalText, String impactSummary,
                       ChallengeStatus status, TeamView team, List<ProgressView> milestones) {}
    private void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Organization access denied"); }
    private void own(Project p, User u) {
        requireRole(u,Role.UNIVERSITY);
        if(u.getOrganization()==null || !p.getUniversity().getId().equals(u.getOrganization().getId())) forbidden();
    }
    private Challenge assigned(UUID id, User u) {
        requireRole(u,Role.UNIVERSITY); Challenge c=workflow.requireChallenge(id);
        if(u.getOrganization()==null || c.getAssignedUniversity()==null || !c.getAssignedUniversity().getId().equals(u.getOrganization().getId())) forbidden();
        if(c.getStatus()!=ChallengeStatus.ASSIGNED || c.getAssignmentResponse()!=AssignmentResponse.ACCEPTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Accepted assignment required");
        return c;
    }
    public Draft draft(UUID id,User u) {
        Challenge c=assigned(id,u);
        try {
            var d=ai.draftProposal(c.getTitle(),c.getDescription(),c.getCategory()==null?"OTHER":c.getCategory().name(),u.getOrganization().getName(),List.of());
            if(d==null || !HttpAiClient.validConfidence(d.confidence()) || d.confidence()<threshold || d.proposal()==null || d.proposal().isBlank()) throw new IllegalStateException();
            return new Draft("DRAFT — "+d.proposal(),true,"GENERATED");
        } catch(RuntimeException e) {
            c.setModerationRequired(true); c.setModerationReason("Proposal AI unavailable or low confidence; manual review required");
            return new Draft("DRAFT — Proposal for "+c.getTitle()+"\nProblem: "+c.getDescription()+"\nObjectives: assess needs and define measurable outcomes.\nApproach: survey, develop a prototype, pilot and evaluate impact.\nTeam, budget and timeline: complete before submission.",true,"TEMPLATE_FALLBACK");
        }
    }
    public View create(UUID id,User u,Proposal input) {
        Challenge c=assigned(id,u);
        if(projects.findByChallengeId(id).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Project already exists");
        Project p=new Project(); p.setChallenge(c);p.setUniversity(u.getOrganization());p.setTitle(input.title());p.setProposalText(input.proposalText());projects.saveAndFlush(p);
        Team t=new Team();t.setProject(p);t.setName(input.teamName());teams.save(t);
        workflow.transition(id,u,new WorkflowService.Command(ChallengeStatus.PROPOSAL_CREATED,null,null,null,null));
        return view(p);
    }
    public Project project(UUID id) { return projects.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Project not found")); }
    private boolean supportable(Project p) { return Set.of(ChallengeStatus.PROPOSAL_CREATED,ChallengeStatus.INDUSTRY_CONNECTED,ChallengeStatus.PROTOTYPE,ChallengeStatus.PILOT,ChallengeStatus.DEPLOYED,ChallengeStatus.IMPACT_EVALUATED).contains(p.getChallenge().getStatus()); }
    public View read(UUID id,User u) {
        Project p=project(id);
        if(u.getRole()==Role.UNIVERSITY) own(p,u);
        else if(u.getRole()==Role.INDUSTRY) { industry(u); if(!supportable(p)) forbidden(); }
        else if(u.getRole()!=Role.ADMIN && u.getRole()!=Role.GOVERNMENT) forbidden();
        return u.getRole()==Role.INDUSTRY ? supportFacing(p) : view(p);
    }
    public View edit(UUID id,User u,Edit input) {
        Project p=project(id);own(p,u);entityManager.lock(p, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        if(input.proposalText()!=null) { requireText(input.proposalText(),"Proposal required");p.setProposalText(input.proposalText()); }
        if(input.impactSummary()!=null) p.setImpactSummary(input.impactSummary());
        notify(p,"Project proposal or impact updated");return view(p);
    }
    public View team(UUID id,User u,TeamInput input) {
        Project p=project(id);own(p,u);entityManager.lock(p, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        require(input.members().stream().filter(m->m.role()==TeamRole.LEAD).count()<=1,"Only one team lead allowed");
        require(input.members().stream().map(Member::facultyProfileId).distinct().count()==input.members().size(),"Duplicate faculty member");
        Team t=teams.findByProjectId(id).orElseGet(()->{Team n=new Team();n.setProject(p);return n;});t.setName(input.name());teams.save(t);
        var profiles=input.members().stream().map(m->{var f=faculty.findById(m.facultyProfileId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Faculty not found"));
            require(f.getOrganization().getId().equals(p.getUniversity().getId()),"Faculty must belong to project university");return f;}).toList();
        members.deleteAll(members.findByTeamId(t.getId()));members.flush();
        for(int i=0;i<profiles.size();i++){TeamMember m=new TeamMember();m.setTeam(t);m.setFacultyProfile(profiles.get(i));m.setRole(input.members().get(i).role());members.save(m);}
        notify(p,"Project team updated");return view(p);
    }
    public ProgressView progress(UUID id,UUID milestoneId,User u,Progress input) {
        Project p=project(id);own(p,u);entityManager.lock(p, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        Milestone m=milestoneId==null?new Milestone():milestones.findById(milestoneId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Milestone not found"));
        if(m.getProject()!=null && !m.getProject().getId().equals(id)) forbidden();
        if(input.status()==MilestoneStatus.COMPLETED) requireText(input.outcome(),"Completed milestone requires outcome");
        m.setProject(p);m.setTitle(input.title());m.setDescription(input.description());m.setDueDate(input.dueDate());m.setStatus(input.status());m.setOutcome(input.outcome());
        m.setCompletedAt(input.status()==MilestoneStatus.COMPLETED?(m.getCompletedAt()==null?clock.instant():m.getCompletedAt()):null);
        milestones.save(m);notify(p,"Project progress updated: "+input.title());return progressView(m);
    }
    private void industry(User u) { requireRole(u,Role.INDUSTRY);require(u.getOrganization()!=null && u.getOrganization().getType()==OrganizationType.INDUSTRY,"Industry organization required"); }
    public SupportView pledge(UUID id,User u,Support input) {
        industry(u);Project p=project(id);entityManager.lock(p, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);require(supportable(p),"Project not ready for support");
        if(input.supportType()==SupportType.FUNDING) require(input.amount()!=null && input.amount().signum()>0 && input.currency()!=null,"Funding requires positive amount and currency");
        else require(input.amount()==null && input.currency()==null,"Only funding has amount and currency");
        Pledge s=new Pledge();s.setProject(p);s.setIndustryOrganization(u.getOrganization());s.setCreatedBy(u);s.setSupportType(input.supportType());s.setAmount(input.amount());s.setCurrency(input.currency());s.setDescription(input.description());pledges.saveAndFlush(s);
        if(p.getChallenge().getStatus()==ChallengeStatus.PROPOSAL_CREATED) workflow.transition(p.getChallenge().getId(),u,new WorkflowService.Command(ChallengeStatus.INDUSTRY_CONNECTED,null,null,null,null));
        else notify(p,"Industry pledge recorded");
        return supportView(s);
    }
    public Page<SupportView> pledges(User u,int page,int size) { industry(u);return pledges.findByIndustryOrganizationId(u.getOrganization().getId(),paging(page,size)).map(this::supportView); }
    public Page<View> matched(User u,int page,int size) {
        industry(u);var list=projects.findAll().stream().filter(this::supportable).sorted(Comparator.comparing(Project::getCreatedAt).thenComparing(Project::getId)).toList();
        var paging=paging(page,size);int start=(int)Math.min(paging.getOffset(),list.size());return new PageImpl<>(list.subList(start,Math.min(start+size,list.size())).stream().map(this::supportFacing).toList(),paging,list.size());
    }
    public static PageRequest paging(int page,int size) { require(page>=0 && size>0 && size<=100,"Invalid pagination");return PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.asc("id"))); }
    private void notify(Project p,String message) { notifications.notify(p.getChallenge(),null,null,message); }
    private SupportView supportView(Pledge p){return new SupportView(p.getId(),p.getProject().getId(),p.getIndustryOrganization().getId(),p.getSupportType(),p.getAmount(),p.getCurrency(),p.getDescription());}
    private ProgressView progressView(Milestone m){return new ProgressView(m.getId(),m.getTitle(),m.getDescription(),m.getDueDate(),m.getStatus(),m.getCompletedAt(),m.getOutcome());}
    private View supportFacing(Project p) { return new View(p.getId(),p.getChallenge().getId(),p.getUniversity().getId(),p.getTitle(),null,p.getImpactSummary(),p.getChallenge().getStatus(),null,milestones.findByProjectId(p.getId()).stream().map(this::progressView).toList()); }
    private View view(Project p) {
        TeamView t=teams.findByProjectId(p.getId()).map(v->new TeamView(v.getId(),v.getName(),members.findByTeamId(v.getId()).stream().map(m->new MemberView(m.getId(),m.getFacultyProfile().getId(),m.getFacultyProfile().getName(),m.getRole())).toList())).orElse(null);
        return new View(p.getId(),p.getChallenge().getId(),p.getUniversity().getId(),p.getTitle(),p.getProposalText(),p.getImpactSummary(),p.getChallenge().getStatus(),t,milestones.findByProjectId(p.getId()).stream().map(this::progressView).toList());
    }
}
