package in.sih.platform.project;

import com.fasterxml.jackson.databind.*;
import in.sih.platform.ai.AiClient;
import in.sih.platform.challenge.*;
import in.sih.platform.user.*;
import in.sih.platform.organization.*;
import in.sih.platform.notification.*;
import in.sih.platform.pledge.*;
import in.sih.platform.workflow.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:plannedapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class PlannedApiIntegrationTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json; @Autowired JwtEncoder encoder;
    @Autowired UserRepository users; @Autowired ChallengeRepository challenges; @Autowired ChallengeService intake;
    @Autowired WorkflowService workflow; @Autowired FacultyProfileRepository faculty; @Autowired OrganizationRepository organizations;
    @Autowired NotificationRepository notifications; @Autowired ChallengeEventRepository events; @Autowired PledgeRepository pledges;
    @Autowired ProjectRepository projects;
    @Autowired TeamRepository teams; @Autowired TeamMemberRepository members;
    @MockBean AiClient ai;
    User user(Role r){return users.findByPhone("900000000"+r.ordinal()).orElseThrow();}
    String token(Role r){return token(user(r));}
    String token(User u){var claims=JwtClaimsSet.builder().issuer("sih-platform").subject(u.getId().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("role",u.getRole().name()).build();return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();}

    JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,Role role,Object body,int expected) throws Exception {
        if(role!=null)request.header("Authorization",token(role));
        if(body!=null)request.contentType("application/json").content(json.writeValueAsBytes(body));
        String out=http.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isBlank()?null:json.readTree(out);
    }
    Object audio(){return Map.of("audioBase64","YQ==","mimeType","audio/wav","language","en");}
    @Test void securityAndValidation() throws Exception {
        String[] paths={"/api/users/me/challenges","/api/challenges/transcription","/api/university/inbox","/api/users","/api/organizations/me/departments/New/faculty"};
        for(String path:paths){
            var request=path.endsWith("faculty")?put(path):path.endsWith("transcription")||path.equals("/api/users")?post(path):get(path);
            send(request,null,null,401);
            request=path.endsWith("faculty")?put(path):path.endsWith("transcription")||path.equals("/api/users")?post(path):get(path);
            send(request,Role.INDUSTRY,path.endsWith("transcription")?audio():path.equals("/api/users")?Map.of("name","Admin","phone","9888888888","role","ADMIN"):path.endsWith("faculty")?List.of():null,403);
        }
        send(get(paths[0]+"?size=0"),Role.CITIZEN,null,400);
        send(get(paths[2]+"?assignmentResponse=INVALID"),Role.UNIVERSITY,null,400);
        send(post(paths[1]),Role.CITIZEN,Map.of("audioBase64","!","mimeType","audio/wav","language","en"),400);
        send(post(paths[1]),Role.CITIZEN,Map.of("audioBase64","YQ==","mimeType","text/plain","language","en"),400);
        send(post(paths[1]),Role.CITIZEN,Map.of("audioBase64",Base64.getEncoder().encodeToString(new byte[1048577]),"mimeType","audio/wav","language","en"),400);
        send(post(paths[3]),Role.ADMIN,Map.of("name","","phone","bad","role","ADMIN"),400);
        send(put(paths[4]),Role.UNIVERSITY,List.of(Map.of("name","","bio","","expertiseTags",List.of())),400);
    }
    @Test void transcriptionSuccessAndFallback() throws Exception {
        when(ai.transcribe(anyString(),anyString(),anyString())).thenReturn(new AiClient.Transcription("Water problem",.95));
        assertEquals("TRANSCRIBED",send(post("/api/challenges/transcription"),Role.CITIZEN,audio(),200).get("status").asText());
        when(ai.transcribe(anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("down"));
        var fallback=send(post("/api/challenges/transcription"),Role.CITIZEN,audio(),200);
        assertEquals("MANUAL_ENTRY",fallback.get("status").asText());assertTrue(fallback.get("text").isNull());
        doReturn(new AiClient.Transcription("uncertain",.1)).when(ai).transcribe(anyString(),anyString(),anyString());
        assertEquals("MANUAL_ENTRY",send(post("/api/challenges/transcription"),Role.CITIZEN,audio(),200).get("status").asText());
    }
    UUID submit(){return intake.submit(user(Role.CITIZEN).getId(),new ChallengeInput("Test report","Unsafe supply",20.,77.,"Test",null,3),null,null).id();}
    @Test void ownIncludesPendingRejectedAndTimelineIsSanitized() throws Exception {
        UUID id=submit();
        assertTrue(send(get("/api/users/me/challenges"),Role.CITIZEN,null,200).toString().contains(id.toString()));
        workflow.transition(id,user(Role.ADMIN),new WorkflowService.Command(ChallengeStatus.REJECTED,"Outside scope",null,null,null));
        var own=send(get("/api/users/me/challenges"),Role.CITIZEN,null,200);
        assertTrue(own.toString().contains("Outside scope"));assertFalse(own.toString().contains("aiStatus"));
        var detail=send(get("/api/challenges/"+id),null,null,200);
        for(var event:detail.get("timeline")){assertEquals(3,event.size());assertFalse(event.has("actorId"));assertFalse(event.has("reason"));}
        User citizen=user(Role.CITIZEN);citizen.setPhone("9999999999");users.flush();
        User other=new User();other.setPhone("9000000000");other.setName("Other");other.setRole(Role.CITIZEN);other.setActive(true);users.saveAndFlush(other);
        assertFalse(send(get("/api/users/me/challenges"),Role.CITIZEN,null,200).toString().contains(id.toString()));
    }
    @Test void departmentPublicAndReplacementScopeAndReferences() throws Exception {
        UUID org=user(Role.UNIVERSITY).getOrganization().getId();String path="/api/organizations/me/departments/New/faculty";
        var body=List.of(Map.of("name","New Faculty","bio","Water","expertiseTags",List.of("water")));
        var created=send(put(path),Role.UNIVERSITY,body,200);UUID id=UUID.fromString(created.get(0).get("id").asText());
        var publicView=send(get("/api/organizations/"+org+"/departments"),null,null,200);
        assertFalse(publicView.toString().contains("email"));assertFalse(publicView.toString().contains("user"));
        send(get("/api/organizations/invalid/departments"),null,null,400);
        send(get("/api/organizations/"+user(Role.INDUSTRY).getOrganization().getId()+"/departments"),null,null,404);
        var f=faculty.findById(id).orElseThrow();f.setUser(user(Role.UNIVERSITY));
        send(put(path),Role.UNIVERSITY,List.of(),409);f.setUser(null);
        send(put(path),Role.UNIVERSITY,List.of(Map.of("id",id,"name","Updated","bio","New","expertiseTags",List.of("health"))),200);
        var foreign=faculty.findAll().stream().filter(x->!x.getOrganization().getId().equals(org)).findFirst().orElseThrow();
        send(put(path),Role.UNIVERSITY,List.of(Map.of("id",foreign.getId(),"name","Foreign","bio","","expertiseTags",List.of())),403);
        Project project=new Project();project.setChallenge(challenges.findById(submit()).orElseThrow());project.setUniversity(user(Role.UNIVERSITY).getOrganization());project.setTitle("Project");project.setProposalText("Proposal");projects.save(project);
        Team team=new Team();team.setProject(project);team.setName("Team");teams.save(team);
        TeamMember member=new TeamMember();member.setTeam(team);member.setFacultyProfile(f);member.setRole(TeamRole.LEAD);members.saveAndFlush(member);
        var conflict=send(put(path),Role.UNIVERSITY,List.of(),409);assertTrue(conflict.toString().contains("Updated"));
        members.delete(member);members.flush();
        send(put(path),Role.UNIVERSITY,List.of(),200);assertFalse(faculty.existsById(id));
    }
    @Test void provisioningAndInboxScope() throws Exception {
        UUID org=user(Role.UNIVERSITY).getOrganization().getId();
        Object account=Map.of("name","New account","phone","9876543210","role","UNIVERSITY","organizationId",org);
        var response=send(post("/api/users"),Role.ADMIN,account,201);assertFalse(response.has("phone"));
        send(post("/api/users"),Role.ADMIN,account,409);
        send(post("/api/users"),Role.ADMIN,Map.of("name","Wrong","phone","9876543211","role","INDUSTRY","organizationId",org),400);
        UUID id=submit();Challenge c=challenges.findById(id).orElseThrow();c.setAssignedUniversity(user(Role.UNIVERSITY).getOrganization());c.setAssignmentResponse(AssignmentResponse.PENDING);c.setResponseDeadline(Instant.now().minusSeconds(1));
        var inbox=send(get("/api/university/inbox?assignmentResponse=PENDING"),Role.UNIVERSITY,null,200);
        assertTrue(inbox.toString().contains(id.toString()));assertTrue(inbox.get("content").get(0).get("overdue").asBoolean());
        user(Role.UNIVERSITY).setOrganization(organizations.findAll().stream().filter(o->o.getType()==OrganizationType.UNIVERSITY&&!o.getId().equals(org)).findFirst().orElseThrow());
        assertFalse(send(get("/api/university/inbox"),Role.UNIVERSITY,null,200).toString().contains(id.toString()));
    }
    @Test void frontendContracts() throws Exception {
        var docs=send(get("/v3/api-docs"),null,null,200);
        assertEquals("bearer",docs.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText());
        http.perform(options("/api/users/me").header("Origin","http://localhost:5173").header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","Authorization")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        http.perform(options("/api/users/me").header("Origin","https://other.example").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
        for(var error:List.of(send(get("/api/users/me"),null,null,401),send(get("/api/university/inbox"),Role.CITIZEN,null,403),send(get("/api/users/me/challenges?size=0"),Role.CITIZEN,null,400))) {
            assertTrue(error.has("timestamp"));assertTrue(error.has("status"));assertTrue(error.has("code"));assertTrue(error.has("message"));assertTrue(error.has("path"));assertFalse(error.has("trace"));
        }
        UUID id=submit();
        assertEquals(0,send(get("/api/challenges/"+id),null,null,200).path("allowedTransitions").size());
        assertEquals(2,send(get("/api/challenges/"+id),Role.ADMIN,null,200).path("allowedTransitions").size());
        assertEquals(0,send(get("/api/challenges/"+id),Role.CITIZEN,null,200).path("allowedTransitions").size());
        long before=send(get("/api/government/analytics"),Role.GOVERNMENT,null,200).path("uniqueIncidents").asLong();
        UUID duplicate=submit();challenges.findById(duplicate).orElseThrow().setDuplicateOf(challenges.findById(id).orElseThrow());
        assertEquals(before,send(get("/api/government/analytics"),Role.GOVERNMENT,null,200).path("uniqueIncidents").asLong());
    }
}
