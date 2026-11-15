package in.sih.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import in.sih.platform.challenge.*;
import in.sih.platform.matching.*;
import in.sih.platform.organization.*;
import in.sih.platform.user.*;
import in.sih.platform.workflow.*;
import in.sih.platform.notification.*;
import java.net.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:matching;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "workflow.deadline-check-interval=3600000"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class AiMatchingIntegrationTest {
    static HttpServer server;
    static volatile String mode="ok";
    static {
        try {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/", exchange -> {
                String request=new String(exchange.getRequestBody().readAllBytes());
                String current=mode;
                if(current.equals("timeout")) try { Thread.sleep(500); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
                String body=exchange.getRequestURI().getPath().equals("/classify")
                    ? "{\"category\":\"WATER_SANITATION\",\"confidence\":"+(current.equals("low")?"0.2":"0.94")+"}"
                    : "{\"embedding\":"+(request.contains("specialist")?"[0,1]":"[1,0]")+",\"model\":\"test-v1\",\"dimensions\":2,\"confidence\":1}";
                if(current.equals("invalid")) body="{\"embedding\":[1],\"model\":\"x\",\"dimensions\":2,\"confidence\":2}";
                byte[] bytes=body.getBytes(); exchange.sendResponseHeaders(current.equals("error")?503:200,bytes.length);
                try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
            }); server.start();
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("ai.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort()); r.add("ai.timeout",()->"200ms");
    }
    @Autowired ChallengeService service;
    @Autowired ChallengeRepository challenges;
    @Autowired UserRepository users;
    @Autowired WorkflowService workflow;
    @Autowired DuplicateService duplicates;
    @Autowired MatchingService matching;
    @Autowired FacultyProfileRepository faculty;
    @Autowired OrganizationRepository organizations;
    @Autowired AssignmentRecordRepository history;
    @Autowired AssignmentDeadlineJob deadlines;
    @Autowired NotificationRepository notifications;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    String token(Role role) {
        var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("sih-platform").subject(user(role).getId().toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("role",role.name()).build();
        return "Bearer "+encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    @Test void adminRoutesEnforceRoleAndExposeQueuesAndMerge() throws Exception {
        var original=submit(); var c=submit(); String path="/api/admin/challenges/"+c.getId();
        for(Role role:Role.values()) {
            http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path+"/duplicates").header("Authorization",token(role)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(role==Role.ADMIN?200:403));
        }
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/challenges").header("Authorization",token(Role.ADMIN)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path+"/classification").header("Authorization",token(Role.ADMIN))
            .contentType("application/json").content("{\"category\":\"HEALTH\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(Category.HEALTH,c.getCategory());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/merge").header("Authorization",token(Role.ADMIN))
            .contentType("application/json").content(json.writeValueAsBytes(Map.of("originalId",original.getId()))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.duplicateOfId").value(original.getId().toString()));
    }
    @BeforeEach void reset() { mode="ok"; }
    @AfterAll static void stop() { server.stop(0); }
    User user(Role role) { return users.findByPhone("900000000"+role.ordinal()).orElseThrow(); }
    Challenge submit() {
        UUID id=service.submit(user(Role.CITIZEN).getId(),new ChallengeInput("Water issue","Unsafe supply",20.0,77.0,"Test",null,2),null,null).id();
        return challenges.findById(id).orElseThrow();
    }
    @Test void enrichesWithoutChangingStateOrConfirmingCategory() {
        Challenge c=submit(); assertEquals(Category.WATER_SANITATION,c.getSuggestedCategory()); assertNull(c.getCategory());
        assertEquals(.94,c.getClassificationConfidence()); assertEquals("test-v1",c.getEmbeddingModel());
        assertFalse(c.getModerationRequired()); assertEquals(ChallengeStatus.SUBMITTED,c.getStatus());
    }
    @Test void duplicateFlagMergeAndRetryDoNotDoubleCount() {
        Challenge original=submit(), duplicate=submit();
        assertTrue(duplicate.getDuplicateCandidate()); assertEquals(original.getId(),duplicates.candidates(duplicate).getFirst().id());
        duplicates.merge(duplicate,original,user(Role.ADMIN)); duplicates.merge(duplicate,original,user(Role.ADMIN));
        challenges.flush(); assertEquals(4,original.getAffectedPeople()); assertEquals(original,duplicate.getDuplicateOf());
        assertThrows(ResponseStatusException.class,()->duplicates.merge(original,original,user(Role.ADMIN)));
        assertThrows(ResponseStatusException.class,()->duplicates.merge(original,duplicate,user(Role.CITIZEN)));
    }
    @Test void duplicateRequiresCompatibleVectorsAndNearbyLocation() {
        Challenge original=submit(), c=submit(); original.setEmbeddingModel("different"); assertTrue(duplicates.candidates(c).isEmpty());
        original.setEmbeddingModel("test-v1"); original.setEmbeddingText("[1,0,0]"); assertTrue(duplicates.candidates(c).isEmpty());
        original.setEmbeddingText("[0,1]"); assertTrue(duplicates.candidates(c).isEmpty());
        original.setEmbeddingText("[1,0]"); original.setLatitude(30); assertTrue(duplicates.candidates(c).isEmpty());
    }
    @Test void lowConfidenceAndMalformedAndHttpErrorsRemainUsable() {
        for(String failure:List.of("low","invalid","error")) {
            mode=failure; Challenge c=submit(); assertTrue(c.getModerationRequired()); assertNotNull(c.getModerationReason());
            assertEquals(ChallengeStatus.SUBMITTED,c.getStatus());
        }
    }
    @Test void timeoutDoesNotFailSubmission() {
        mode="timeout"; long start=System.nanoTime(); Challenge c=submit();
        assertTrue(c.getModerationRequired()); assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1800);
    }
    @Test void stoppedServiceFallsBackAndPersistsSubmission() throws Exception {
        HttpServer stopped=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); int port=stopped.getAddress().getPort(); stopped.start(); stopped.stop(0);
        var client=new HttpAiClient(json,"http://127.0.0.1:"+port,Duration.ofMillis(100));
        var enrichment=new ChallengeEnrichment(client,json,.7);
        var intake=new ChallengeService(challenges,users,workflow,storage,enrichment,duplicates);
        long before=challenges.count();
        UUID id=intake.submit(user(Role.CITIZEN).getId(),new ChallengeInput("Issue","Description",20.0,77.0,"Test",null,1),null,null).id();
        challenges.flush(); assertEquals(before+1,challenges.count()); assertTrue(challenges.findById(id).orElseThrow().getModerationRequired());
    }
    @Autowired LocalAttachmentStorage storage;
    @Test void rankingIncludesFacultyRulesAndSemanticReasonsAndAdminOverride() {
        Challenge c=submit(); c.setCategory(Category.WATER_SANITATION);
        var profiles=faculty.findAll(); var target=profiles.getFirst();
        target.setBio("Water treatment"); target.getExpertiseTags().clear(); target.getExpertiseTags().add("water");
        Organization best=target.getOrganization(); best.setLatitude(c.getLatitude()); best.setLongitude(c.getLongitude());
        for(var f:profiles) if(!f.getId().equals(target.getId())) { f.setBio("specialist"); f.setDepartment("specialist"); f.getExpertiseTags().clear(); }
        var ranked=matching.rank(c); assertEquals(best.getId(),ranked.getFirst().id());
        assertEquals(target.getId(),ranked.getFirst().faculty().getFirst().id());
        assertTrue(ranked.getFirst().faculty().getFirst().reasons().getFirst().contains("Embedding similarity"));
        assertTrue(ranked.getFirst().reasons().stream().anyMatch(s->s.startsWith("Current workload")));
        Organization override=organizations.findAll().stream().filter(o->o.getType()==OrganizationType.UNIVERSITY && !o.getId().equals(best.getId())).findFirst().orElseThrow();
        c.setStatus(ChallengeStatus.CATEGORIZED); workflow.assign(c.getId(),user(Role.ADMIN),override.getId()); assertEquals(override,c.getAssignedUniversity());
    }
    @Test void overdueJobIsIdempotentPreservesStateAndBlocksLateResponse() {
        Challenge c=submit(); c.setStatus(ChallengeStatus.CATEGORIZED);
        workflow.assign(c.getId(),user(Role.ADMIN),user(Role.UNIVERSITY).getOrganization().getId());
        c.setResponseDeadline(Instant.now().minusSeconds(1)); long before=notifications.count();
        deadlines.flagOverdue(); deadlines.flagOverdue();
        assertTrue(c.getReassignmentRequired()); assertEquals(ChallengeStatus.ASSIGNED,c.getStatus());
        assertEquals(before+3,notifications.count());
        assertEquals("OVERDUE",history.findFirstByChallengeIdOrderByCreatedAtDesc(c.getId()).orElseThrow().getResponse());
        assertThrows(ResponseStatusException.class,()->workflow.respond(c.getId(),user(Role.UNIVERSITY),AssignmentResponse.ACCEPTED,null));
        workflow.genericTransition(c.getId(),user(Role.ADMIN),new WorkflowService.Command(ChallengeStatus.CATEGORIZED,"Reassign",null,null,null));
        assertFalse(c.getReassignmentRequired()); assertNull(c.getAssignedUniversity());
    }
    @Test void matchingFallsBackToRulesWhenAiFails() {
        Challenge c=submit(); mode="error";
        var ranked=matching.rank(c); assertEquals(10,ranked.size());
        assertTrue(ranked.stream().allMatch(u->Double.isFinite(u.score()) && !u.faculty().isEmpty()));
        assertTrue(ranked.getFirst().faculty().getFirst().reasons().getFirst().contains("rules used"));
    }
    @Test void duplicateBoundariesAreInclusive() {
        Challenge original=submit(), c=submit();
        var strict=new DuplicateService(challenges,new VectorSupport(json,new CosineSimilarityService()),new HaversineGeoService(),1,0);
        assertEquals(original.getId(),strict.candidates(c).getFirst().id());
    }
    @Test void rankingUsesWorkloadAndAcceptanceHistory() {
        Challenge c=submit(); var university=user(Role.UNIVERSITY).getOrganization();
        double baseline=matching.rank(c).stream().filter(u->u.id().equals(university.getId())).findFirst().orElseThrow().score();
        Challenge assigned=submit(); assigned.setStatus(ChallengeStatus.CATEGORIZED);
        workflow.assign(assigned.getId(),user(Role.ADMIN),university.getId());
        double loaded=matching.rank(c).stream().filter(u->u.id().equals(university.getId())).findFirst().orElseThrow().score();
        assertTrue(loaded<baseline);
        workflow.respond(assigned.getId(),user(Role.UNIVERSITY),AssignmentResponse.ACCEPTED,null);
        double accepted=matching.rank(c).stream().filter(u->u.id().equals(university.getId())).findFirst().orElseThrow().score();
        assertTrue(accepted>loaded);
    }
    @Test void acceptanceHistorySurvivesRecallAndDecline() {
        Challenge c=submit(); c.setStatus(ChallengeStatus.CATEGORIZED); var university=user(Role.UNIVERSITY);
        workflow.assign(c.getId(),user(Role.ADMIN),university.getOrganization().getId());
        workflow.respond(c.getId(),university,AssignmentResponse.ACCEPTED,null);
        workflow.genericTransition(c.getId(),user(Role.ADMIN),new WorkflowService.Command(ChallengeStatus.CATEGORIZED,"Recall",null,null,null));
        workflow.assign(c.getId(),user(Role.ADMIN),university.getOrganization().getId());
        workflow.respond(c.getId(),university,AssignmentResponse.DECLINED,"No capacity");
        var records=history.findAll(); assertEquals(1,records.stream().filter(a->a.getResponse().equals("ACCEPTED")).count());
        assertEquals(1,records.stream().filter(a->a.getResponse().equals("DECLINED")).count());
    }
}
