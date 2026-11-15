package in.sih.platform.user;

import in.sih.platform.ai.*;
import in.sih.platform.challenge.*;
import in.sih.platform.organization.*;
import in.sih.platform.project.*;
import in.sih.platform.workflow.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PlannedApiService {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final FacultyProfileRepository faculty;
    private final TeamMemberRepository members;
    private final ChallengeRepository challenges;
    private final ChallengeEventRepository events;
    private final ProjectRepository projects;
    private final ChallengeService intake;
    private final AiClient ai;
    private final double threshold;
    public PlannedApiService(UserRepository users, OrganizationRepository organizations, FacultyProfileRepository faculty,
            TeamMemberRepository members, ChallengeRepository challenges, ChallengeEventRepository events,
            ProjectRepository projects, ChallengeService intake, AiClient ai, @Value("${ai.confidence-threshold:0.70}") double threshold) {
        this.users=users; this.organizations=organizations; this.faculty=faculty; this.members=members;
        this.challenges=challenges; this.events=events; this.projects=projects; this.intake=intake; this.ai=ai; this.threshold=threshold;
    }
    private Pageable paging(int page,int size) {
        WorkflowService.require(page>=0 && size>=1 && size<=100,"Page must be nonnegative and size between 1 and 100");
        return PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.asc("id")));
    }
    public record OwnChallenge(UUID id,String title,ChallengeStatus status,Category category,int affectedPeople,Instant fixedConfirmedAt,String rejectionReason) { }
    @Transactional(readOnly=true)
    public Page<OwnChallenge> own(UUID actor,int page,int size) {
        WorkflowService.requireRole(intake.actor(actor),Role.CITIZEN);
        return challenges.findAll((r,q,b)->b.equal(r.get("submittedBy").get("id"),actor),paging(page,size)).map(c->{
            String reason=null;
            if(c.getStatus()==ChallengeStatus.REJECTED) {
                var history=events.findByChallengeIdOrderByCreatedAtAsc(c.getId());
                if(!history.isEmpty()) reason=history.getLast().getReason();
            }
            return new OwnChallenge(c.getId(),c.getTitle(),c.getStatus(),c.getCategory(),c.getAffectedPeople(),c.getFixedConfirmedAt(),reason);
        });
    }
    public record Transcript(String text,String status,String message) { }
    public Transcript transcribe(UUID actor,PlannedApiController.Audio input) {
        WorkflowService.requireRole(intake.actor(actor),Role.CITIZEN);
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(input.audioBase64()); }
        catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid base64 audio");}
        WorkflowService.require(bytes.length>0 && bytes.length<=1048576,"Audio must contain 1 to 1048576 bytes");
        try {
            var result=ai.transcribe(input.audioBase64(),input.mimeType(),input.language());
            if(result!=null && result.text()!=null && !result.text().isBlank() && HttpAiClient.validConfidence(result.confidence()) && result.confidence()>=threshold)
                return new Transcript(result.text(),"TRANSCRIBED","Transcription ready");
        } catch(RuntimeException ignored) { }
        return new Transcript(null,"MANUAL_ENTRY","Transcription unavailable; please enter the problem description manually.");
    }
    public record Faculty(UUID id,String name,Set<String> expertiseTags,String bio) { }
    private Faculty view(FacultyProfile f){return new Faculty(f.getId(),f.getName(),Set.copyOf(f.getExpertiseTags()),f.getBio());}
    @Transactional(readOnly=true)
    public Map<String,List<Faculty>> departments(UUID id) {
        organizations.findById(id).filter(o->o.getType()==OrganizationType.UNIVERSITY)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"University not found"));
        Map<String,List<Faculty>> result=new TreeMap<>();
        faculty.findByOrganizationIdOrderByDepartmentAscNameAsc(id).forEach(f->result.computeIfAbsent(f.getDepartment(),k->new ArrayList<>()).add(view(f)));
        return result;
    }
    private Organization university(UUID actor) {
        User user=intake.actor(actor); WorkflowService.requireRole(user,Role.UNIVERSITY);
        if(user.getOrganization()==null || user.getOrganization().getType()!=OrganizationType.UNIVERSITY)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"University organization required");
        return user.getOrganization();
    }
    public List<Faculty> replace(UUID actor,String department,List<PlannedApiController.FacultyInput> input) {
        Organization org=university(actor);
        WorkflowService.require(!department.isBlank() && department.length()<=200,"Invalid department");
        var existing=faculty.findByOrganizationIdOrderByDepartmentAscNameAsc(org.getId()).stream().filter(f->f.getDepartment().equals(department)).toList();
        Set<UUID> ids=new HashSet<>();
        for(var item:input) if(item.id()!=null) {
            WorkflowService.require(ids.add(item.id()),"Duplicate faculty id");
            if(existing.stream().noneMatch(f->f.getId().equals(item.id()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Faculty must belong to this organization and department");
        }
        var removed=existing.stream().filter(f->!ids.contains(f.getId())).toList();
        var blocked=removed.stream().filter(f->f.getUser()!=null || members.existsByFacultyProfileId(f.getId())).map(FacultyProfile::getName).toList();
        if(!blocked.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Referenced faculty cannot be deleted: "+String.join(", ",blocked));
        faculty.deleteAll(removed);
        List<Faculty> result=new ArrayList<>();
        for(var item:input) {
            var f=item.id()==null?new FacultyProfile():existing.stream().filter(x->x.getId().equals(item.id())).findFirst().orElseThrow();
            f.setOrganization(org);f.setDepartment(department);f.setName(item.name().trim());f.setBio(item.bio());
            f.setEmail(item.email()==null?(f.getEmail()==null?"":f.getEmail()):item.email());
            f.getExpertiseTags().clear();f.getExpertiseTags().addAll(item.expertiseTags());
            // Faculty embeddings are computed on demand by MatchingService, never persisted.
            result.add(view(faculty.save(f)));
        }
        return result;
    }
    public record Inbox(UUID id,String title,String description,ChallengeStatus status,Category category,
            AssignmentResponse assignmentResponse,Instant responseDeadline,boolean overdue,UUID projectId) { }
    @Transactional(readOnly=true)
    public Page<Inbox> inbox(UUID actor,AssignmentResponse response,int page,int size) {
        UUID org=university(actor).getId();
        return challenges.findAll((r,q,b)->response==null?b.equal(r.get("assignedUniversity").get("id"),org):
            b.and(b.equal(r.get("assignedUniversity").get("id"),org),b.equal(r.get("assignmentResponse"),response)),paging(page,size))
            .map(c->new Inbox(c.getId(),c.getTitle(),c.getDescription(),c.getStatus(),c.getCategory(),c.getAssignmentResponse(),c.getResponseDeadline(),
                c.getAssignmentResponse()==AssignmentResponse.PENDING && c.getResponseDeadline()!=null && !c.getResponseDeadline().isAfter(Instant.now()),
                projects.findByChallengeId(c.getId()).map(Project::getId).orElse(null)));
    }
    public CurrentUserController.Account provision(UUID actor,PlannedApiController.AccountInput input) {
        WorkflowService.requireRole(intake.actor(actor),Role.ADMIN);
        WorkflowService.require(input.role()!=Role.CITIZEN,"Use citizen registration for citizens");
        Organization org=null;
        if(input.role()==Role.ADMIN) WorkflowService.require(input.organizationId()==null,"ADMIN must have no organization");
        else {
            WorkflowService.require(input.organizationId()!=null,"Organization required");
            org=organizations.findById(input.organizationId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Organization not found"));
            WorkflowService.require(org.getType().name().equals(input.role().name()),"Role and organization type must match");
        }
        if(users.existsByPhone(input.phone())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Phone already registered");
        User u=new User();u.setName(input.name().trim());u.setPhone(input.phone());u.setRole(input.role());u.setOrganization(org);u.setActive(true);users.saveAndFlush(u);
        return new CurrentUserController.Account(u.getId(),u.getName(),u.getRole());
    }
}
