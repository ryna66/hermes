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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:collaboration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class CollaborationIntegrationTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json; @Autowired JwtEncoder encoder;
    @Autowired UserRepository users; @Autowired ChallengeRepository challenges; @Autowired ChallengeService intake;
    @Autowired WorkflowService workflow; @Autowired FacultyProfileRepository faculty; @Autowired OrganizationRepository organizations;
    @Autowired NotificationRepository notifications; @Autowired ChallengeEventRepository events; @Autowired PledgeRepository pledges;
    @Autowired ProjectRepository projects;
    @MockBean AiClient ai;
    User user(Role r){return users.findByPhone("900000000"+r.ordinal()).orElseThrow();}
    String token(Role r){return token(user(r));}
    String token(User u){var claims=JwtClaimsSet.builder().issuer("sih-platform").subject(u.getId().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("role",u.getRole().name()).build();return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();}
    UUID assigned(){
        UUID id=intake.submit(user(Role.CITIZEN).getId(),new ChallengeInput("Water issue","Unsafe supply",20.,77.,"Test district",Category.HEALTH,1),null,null).id();
        workflow.transition(id,user(Role.ADMIN),new WorkflowService.Command(ChallengeStatus.VALIDATED,null,null,null,null));
        workflow.transition(id,user(Role.ADMIN),new WorkflowService.Command(ChallengeStatus.CATEGORIZED,null,null,null,null));
        workflow.assign(id,user(Role.ADMIN),user(Role.UNIVERSITY).getOrganization().getId());
        workflow.respond(id,user(Role.UNIVERSITY),AssignmentResponse.ACCEPTED,null);return id;
    }
    JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,Role role,Object body,int status) throws Exception {
        request.header("Authorization",token(role));if(body!=null)request.contentType("application/json").content(json.writeValueAsBytes(body));
        String response=http.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);return response.isBlank()?null:json.readTree(response);
    }
    UUID project()throws Exception{return UUID.fromString(send(post("/api/challenges/"+assigned()+"/project"),Role.UNIVERSITY,Map.of("title","Water project","proposalText","Survey, prototype, pilot","teamName","Water team"),201).get("id").asText());}
    @Test void draftFallbackLowConfidenceAndGeneratedAreAlwaysLabeled()throws Exception {
        // Own expertise is a controlled fixture; unrelated dev faculty must not reach the AI.
        UUID universityId=user(Role.UNIVERSITY).getOrganization().getId();
        for(var profile:faculty.findAll()) {
            profile.getExpertiseTags().clear();
            profile.getExpertiseTags().add(profile.getOrganization().getId().equals(universityId)?"water treatment":"foreign expertise");
        }
        UUID c=assigned();String path="/api/challenges/"+c+"/proposal-draft";
        when(ai.draftProposal(anyString(),anyString(),anyString(),anyString(),anyList())).thenThrow(new IllegalStateException());
        var fallback=send(post(path),Role.UNIVERSITY,null,200);assertTrue(fallback.get("draft").asBoolean());assertTrue(fallback.get("proposal").asText().startsWith("DRAFT"));assertEquals("TEMPLATE_FALLBACK",fallback.get("aiStatus").asText());
        assertTrue(challenges.findById(c).orElseThrow().getModerationRequired());
        doReturn(new AiClient.Draft("AI proposal",.9)).when(ai).draftProposal(anyString(),anyString(),anyString(),anyString(),anyList());
        assertEquals("DRAFT — AI proposal",send(post(path),Role.UNIVERSITY,null,200).get("proposal").asText());
        verify(ai,atLeastOnce()).draftProposal(anyString(),anyString(),anyString(),eq("University 1"),eq(List.of("water treatment")));
        doReturn(new AiClient.Draft("Low",.1)).when(ai).draftProposal(anyString(),anyString(),anyString(),anyString(),anyList());
        assertEquals("TEMPLATE_FALLBACK",send(post(path),Role.UNIVERSITY,null,200).get("aiStatus").asText());
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void actorOrganizationCanBeReadAfterLookupTransactionEnds() {
        User actor=intake.actor(user(Role.UNIVERSITY).getId());
        assertEquals("University 1",actor.getOrganization().getName());
        assertEquals(OrganizationType.UNIVERSITY,actor.getOrganization().getType());
    }
    @ParameterizedTest @EnumSource(Role.class) void roleMatrix(Role r)throws Exception {
        UUID id=project();String path="/api/projects/"+id;
        send(get(path),r,null,r==Role.CITIZEN?403:200);
        send(patch(path),r,Map.of("impactSummary","Updated"),r==Role.UNIVERSITY?200:403);
        send(get("/api/industry/projects"),r,null,r==Role.INDUSTRY?200:403);
        send(get("/api/industry/pledges"),r,null,r==Role.INDUSTRY?200:403);
        send(get("/api/government/analytics"),r,null,r==Role.GOVERNMENT?200:403);
        send(get("/api/notifications"),r,null,200);
        UUID assignment=assigned();
        send(post("/api/challenges/"+assignment+"/proposal-draft"),r,null,r==Role.UNIVERSITY?200:403);
        send(post("/api/challenges/"+assignment+"/project"),r,Map.of("title","Second project","proposalText","Proposal","teamName","Team"),r==Role.UNIVERSITY?201:403);
        send(put(path+"/team"),r,Map.of("name","Team","members",List.of()),r==Role.UNIVERSITY?200:403);
        send(post(path+"/milestones"),r,Map.of("title","Prototype","description","Build","dueDate","2030-01-01","status","PENDING"),r==Role.UNIVERSITY?201:403);
        send(post(path+"/pledges"),r,Map.of("supportType","EQUIPMENT","description","Sensors"),r==Role.INDUSTRY?201:403);
    }
    @Test void teamMembershipMilestonesAndPrototypeEvidence()throws Exception {
        UUID id=project();String path="/api/projects/"+id;
        var own=faculty.findAll().stream().filter(f->f.getOrganization().getId().equals(user(Role.UNIVERSITY).getOrganization().getId())).toList();
        Object lead=Map.of("facultyProfileId",own.getFirst().getId(),"role","LEAD");
        var team=send(put(path+"/team"),Role.UNIVERSITY,Map.of("name","Experts","members",List.of(lead)),200);
        assertEquals(1,team.get("team").get("members").size());assertFalse(team.toString().contains("email"));
        send(put(path+"/team"),Role.UNIVERSITY,Map.of("name","Experts","members",List.of(lead,lead)),400);
        send(put(path+"/team"),Role.UNIVERSITY,Map.of("name","Experts","members",List.of(lead,Map.of("facultyProfileId",own.get(1).getId(),"role","LEAD"))),400);
        var foreign=faculty.findAll().stream().filter(f->!f.getOrganization().getId().equals(user(Role.UNIVERSITY).getOrganization().getId())).findFirst().orElseThrow();
        send(put(path+"/team"),Role.UNIVERSITY,Map.of("name","Experts","members",List.of(Map.of("facultyProfileId",foreign.getId(),"role","MEMBER"))),400);
        var m=send(post(path+"/milestones"),Role.UNIVERSITY,Map.of("title","Prototype","description","Build","dueDate","2030-01-01","status","PENDING"),201);
        String mp=path+"/milestones/"+m.get("id").asText();
        send(patch(mp),Role.UNIVERSITY,Map.of("title","Prototype","description","Built","dueDate","2030-01-01","status","COMPLETED"),400);
        var done=send(patch(mp),Role.UNIVERSITY,Map.of("title","Prototype","description","Built","dueDate","2030-01-01","status","COMPLETED","outcome","Working filter tested"),200);assertFalse(done.get("completedAt").isNull());
        send(post(path+"/pledges"),Role.INDUSTRY,Map.of("supportType","PILOT_SITE","description","Factory site"),201);
        workflow.genericTransition(projects.findById(id).orElseThrow().getChallenge().getId(),user(Role.UNIVERSITY),new WorkflowService.Command(ChallengeStatus.PROTOTYPE,null,null,null,null));
    }
    @Test void fundingValidationFirstPledgeAndAnalytics()throws Exception {
        long initialPledges=send(get("/api/industry/pledges"),Role.INDUSTRY,null,200).get("totalElements").asLong();
        long initialDiscovery=send(get("/api/industry/matched-problems"),Role.INDUSTRY,null,200).get("totalElements").asLong();
        var initialDashboard=send(get("/api/government/analytics"),Role.GOVERNMENT,null,200);
        var initialFunding=initialDashboard.get("fundingByCurrency").path("INR").decimalValue();
        long initialConnected=initialDashboard.get("byStatus").get("INDUSTRY_CONNECTED").asLong();
        UUID id=project();String path="/api/projects/"+id+"/pledges";long eventCount=events.count();
        send(post(path),Role.INDUSTRY,Map.of("supportType","FUNDING","description","Grant"),400);
        send(post(path),Role.INDUSTRY,Map.of("supportType","FUNDING","description","Grant","amount",-1,"currency","INR"),400);
        send(post(path),Role.INDUSTRY,Map.of("supportType","FUNDING","description","Grant","amount",100,"currency","INR"),201);
        assertEquals(eventCount+1,events.count());
        assertTrue(notifications.findAll().stream().anyMatch(n->n.getRecipient().getId().equals(user(Role.INDUSTRY).getId())));
        for(String type:List.of("EQUIPMENT","MENTORSHIP","PILOT_SITE"))send(post(path),Role.INDUSTRY,Map.of("supportType",type,"description","Support"),201);
        assertEquals(eventCount+1,events.count());
        assertEquals(initialPledges+4,send(get("/api/industry/pledges"),Role.INDUSTRY,null,200).get("totalElements").asLong());
        var dashboard=send(get("/api/government/analytics"),Role.GOVERNMENT,null,200);
        assertEquals(0,initialFunding.add(new java.math.BigDecimal("100")).compareTo(dashboard.get("fundingByCurrency").get("INR").decimalValue()));assertEquals(initialConnected+1,dashboard.get("byStatus").get("INDUSTRY_CONNECTED").asLong());assertTrue(dashboard.get("stageSamples").get("ASSIGNED").asInt()>0);
        assertEquals(initialDiscovery+1,send(get("/api/industry/matched-problems"),Role.INDUSTRY,null,200).get("totalElements").asLong());
    }
    @Test void notificationsArePrivateAndReadIsIdempotent()throws Exception {
        project();var n=notifications.findAll().stream().filter(v->v.getRecipient().getId().equals(user(Role.CITIZEN).getId())).findFirst().orElseThrow();
        String path="/api/notifications/"+n.getId()+"/read";
        send(patch(path),Role.INDUSTRY,null,403);
        var first=send(patch(path),Role.CITIZEN,null,200);var second=send(patch(path),Role.CITIZEN,null,200);assertEquals(first.get("readAt"),second.get("readAt"));
        var list=send(get("/api/notifications"),Role.CITIZEN,null,200);assertFalse(list.toString().contains("recipient"));assertFalse(list.toString().contains("phone"));
    }
    @Test void otherProjectMilestoneAndOrganizationPledgesAreProtected()throws Exception {
        UUID first=project(), second=project();
        var m=send(post("/api/projects/"+first+"/milestones"),Role.UNIVERSITY,Map.of("title","Build","description","Build","dueDate","2030-01-01","status","PENDING"),201);
        send(patch("/api/projects/"+second+"/milestones/"+m.get("id").asText()),Role.UNIVERSITY,Map.of("title","Build","description","Build","dueDate","2030-01-01","status","PENDING"),403);
        var createdPledge=send(post("/api/projects/"+first+"/pledges"),Role.INDUSTRY,Map.of("supportType","MENTORSHIP","description","Training"),201);
        User industry=user(Role.INDUSTRY);industry.setOrganization(organizations.findAll().stream().filter(o->o.getType()==OrganizationType.INDUSTRY&&!o.getId().equals(industry.getOrganization().getId())).findFirst().orElseThrow());
        var expectedIds=pledges.findAll().stream()
                .filter(p->p.getIndustryOrganization().getId().equals(industry.getOrganization().getId()))
                .map(p->p.getId().toString()).collect(java.util.stream.Collectors.toSet());
        var visible=send(get("/api/industry/pledges?size=100"),Role.INDUSTRY,null,200);
        Set<String> actualIds=new HashSet<>();visible.get("content").forEach(p->actualIds.add(p.get("id").asText()));
        assertEquals(expectedIds,actualIds);
        assertEquals(expectedIds.size(),visible.get("totalElements").asInt());
        assertFalse(actualIds.contains(createdPledge.get("id").asText()));
    }
    @Test void foreignUniversityAndAnonymousCannotReadOrMutate()throws Exception {
        UUID id=project();User u=user(Role.UNIVERSITY);u.setOrganization(organizations.findAll().stream().filter(o->o.getType()==OrganizationType.UNIVERSITY&&!o.getId().equals(u.getOrganization().getId())).findFirst().orElseThrow());
        send(get("/api/projects/"+id),Role.UNIVERSITY,null,403);
        send(patch("/api/projects/"+id),Role.UNIVERSITY,Map.of("proposalText","Foreign edit"),403);
        http.perform(get("/api/projects/"+id)).andExpect(status().isUnauthorized());
        http.perform(get("/api/government/analytics")).andExpect(status().isUnauthorized());
    }
}
