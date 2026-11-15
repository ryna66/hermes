package in.sih.platform.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.sih.platform.challenge.*;
import in.sih.platform.notification.*;
import in.sih.platform.user.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Transactional
class WorkflowIntegrationTest {
    static final Path UPLOADS = Path.of(System.getProperty("java.io.tmpdir"), "sih-workflow-" + UUID.randomUUID());
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) { registry.add("storage.upload-dir", UPLOADS::toString); }
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ChallengeRepository challenges;
    @Autowired UserRepository users;
    @Autowired ChallengeEventRepository events;
    @Autowired NotificationRepository notifications;
    @Autowired WorkflowService workflow;
    @Autowired ChallengeService service;
    @Autowired JwtEncoder encoder;
    @Autowired PlatformTransactionManager transactions;

    @Test void jsonSubmissionDefaultsAndPublicExplorerAreSanitized() throws Exception {
        long eventCount = events.count(), notificationCount = notifications.count();
        UUID id = submit(null);
        Challenge c = challenges.findById(id).orElseThrow();
        assertEquals(1, c.getAffectedPeople()); assertNull(c.getCategory()); assertEquals(ChallengeStatus.SUBMITTED, c.getStatus());
        assertTrue(c.getModerationRequired()); assertNull(c.getClassificationConfidence());
        assertEquals(eventCount + 1, events.count()); assertEquals(notificationCount + 2, notifications.count());
        var event = events.findByChallengeIdOrderByCreatedAtAsc(id).getFirst();
        assertNull(event.getFromState()); assertEquals(ChallengeStatus.SUBMITTED, event.getToState());
        assertEquals(2, notifications.findAll().stream().filter(n -> n.getChallengeEvent().getId().equals(event.getId())).count());
        http.perform(get("/api/challenges").param("district", "test district").param("status", "SUBMITTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].moderationReason").doesNotExist())
                .andExpect(jsonPath("$.content[0].submittedBy").doesNotExist());
        http.perform(get("/api/challenges").param("district", "Test District").param("category", "HEALTH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        http.perform(get("/api/challenges/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.phone").doesNotExist());
        http.perform(get("/api/challenges").param("size", "101")).andExpect(status().isBadRequest());
    }
    @Test void uploadsStoreBytesAndDoNotExposeLocalPaths() throws Exception {
        byte[] photo = {1, 2, 3}, audio = {4, 5, 6};
        String body = http.perform(multipart("/api/challenges")
                .file(new MockMultipartFile("challenge", "", "application/json", json.writeValueAsBytes(input(Category.HEALTH))))
                .file(new MockMultipartFile("photo", "../../photo.png", "image/png", photo))
                .file(new MockMultipartFile("audio", "audio.wav", "audio/wav", audio))
                .header("Authorization", token(Role.CITIZEN)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.hasPhoto").value(true))
                .andExpect(jsonPath("$.hasAudio").value(true)).andExpect(jsonPath("$.photoPath").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Challenge c = challenges.findById(UUID.fromString(json.readTree(body).get("id").asText())).orElseThrow();
        assertEquals(UPLOADS.toAbsolutePath(), Path.of(c.getPhotoPath()).getParent());
        assertArrayEquals(photo, Files.readAllBytes(Path.of(c.getPhotoPath())));
        assertArrayEquals(audio, Files.readAllBytes(Path.of(c.getAudioPath())));
    }
    @Test void httpEnforcesRolesAndClearTransitionErrors() throws Exception {
        UUID id = submit(Category.HEALTH);
        http.perform(post("/api/challenges/" + id + "/transitions").header("Authorization", token(Role.CITIZEN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"toState\":\"VALIDATED\"}")).andExpect(status().isForbidden());
        http.perform(post("/api/challenges/" + id + "/transitions").header("Authorization", token(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"toState\":\"DEPLOYED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Illegal transition from SUBMITTED to DEPLOYED"));
        long eventCount = events.count(), notificationCount = notifications.count();
        http.perform(post("/api/challenges/" + id + "/transitions").header("Authorization", token(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"toState\":\"VALIDATED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"));
        assertEquals(eventCount + 1, events.count()); assertEquals(notificationCount + 2, notifications.count());
        http.perform(post("/api/challenges").header("Authorization", token(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(input(null)))).andExpect(status().isForbidden());
        http.perform(post("/api/challenges").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input(null))))
                .andExpect(status().isUnauthorized());
    }
    @Test void invalidSubmissionDoesNotCreateRecords() throws Exception {
        long count = challenges.count();
        var input = new HashMap<>(input(null)); input.put("latitude", 91); input.put("affected_people", 0);
        http.perform(post("/api/challenges").header("Authorization", token(Role.CITIZEN)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(input))).andExpect(status().isBadRequest());
        assertEquals(count, challenges.count());
    }
    @Test void assignmentAcceptAndDeclineEnforceOrganizationAndNotifyUniversity() throws Exception {
        UUID id = submit(Category.HEALTH);
        User admin = user(Role.ADMIN), university = user(Role.UNIVERSITY);
        workflow.transition(id, admin, new WorkflowService.Command(ChallengeStatus.VALIDATED, null, null, null, null));
        workflow.transition(id, admin, new WorkflowService.Command(ChallengeStatus.CATEGORIZED, null, null, null, null));
        long notificationCount = notifications.count();
        http.perform(post("/api/admin/challenges/" + id + "/assignment").header("Authorization", token(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("universityId", university.getOrganization().getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        assertEquals(notificationCount + 3, notifications.count());
        Challenge c = challenges.findById(id).orElseThrow(); assertNotNull(c.getResponseDeadline());
        http.perform(post("/api/challenges/" + id + "/assignment-response").header("Authorization", token(Role.UNIVERSITY))
                .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"ACCEPTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        assertEquals(AssignmentResponse.ACCEPTED, c.getAssignmentResponse());
        workflow.transition(id, admin, new WorkflowService.Command(ChallengeStatus.CATEGORIZED, "Recall", null, null, null));
        workflow.assign(id, admin, university.getOrganization().getId());
        notificationCount = notifications.count();
        http.perform(post("/api/challenges/" + id + "/assignment-response").header("Authorization", token(Role.UNIVERSITY))
                .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"DECLINED\",\"reason\":\"No capacity\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CATEGORIZED"));
        assertNull(c.getAssignedUniversity()); assertNull(c.getAssignmentResponse());
        assertEquals(notificationCount + 3, notifications.count());
    }
    @Test void confirmFixedIsOwnedIdempotentFeedbackWithoutAnExtraState() throws Exception {
        UUID id = submit(null); String path = "/api/challenges/" + id + "/confirm-fixed";
        http.perform(post(path).header("Authorization", token(Role.CITIZEN))).andExpect(status().isConflict());
        Challenge c = challenges.findById(id).orElseThrow(); c.setStatus(ChallengeStatus.DEPLOYED);
        User other = new User(); other.setName("Other"); other.setPhone("9888888888"); other.setRole(Role.CITIZEN); other.setActive(true); users.save(other);
        assertEquals(403, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> workflow.confirmFixed(id, other)).getStatusCode().value());
        long count = events.count(), notificationCount = notifications.count();
        http.perform(post(path).header("Authorization", token(Role.CITIZEN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fixedConfirmedAt").isNotEmpty()).andExpect(jsonPath("$.status").value("DEPLOYED"));
        http.perform(post(path).header("Authorization", token(Role.CITIZEN))).andExpect(status().isOk());
        assertEquals(count + 1, events.count()); assertEquals(notificationCount + 2, notifications.count());
    }
    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rollbackRemovesUploadsEventsNotificationsAndChallenge() throws Exception {
        long count = challenges.count(), eventCount = events.count(), notificationCount = notifications.count();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var submitted = service.submit(user(Role.CITIZEN).getId(), new ChallengeInput("Issue", "Text", 20.0, 77.0, "Rollback", null, null),
                    new MockMultipartFile("photo", "p.png", "image/png", new byte[]{1}), null);
            workflow.transition(submitted.id(), user(Role.ADMIN),
                    new WorkflowService.Command(ChallengeStatus.VALIDATED, null, null, null, null));
            challenges.flush();
            tx.setRollbackOnly();
        });
        assertEquals(count, challenges.count()); assertEquals(eventCount, events.count()); assertEquals(notificationCount, notifications.count());
        if (Files.exists(UPLOADS)) try (var files = Files.list(UPLOADS)) { assertEquals(0, files.count()); }
    }
    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void invalidSecondUploadRemovesFirstFileAndLeavesDatabaseUnchanged() throws Exception {
        long count = challenges.count(), eventCount = events.count(), notificationCount = notifications.count();
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
            service.submit(user(Role.CITIZEN).getId(), new ChallengeInput("Issue", "Text", 20.0, 77.0, "Rollback", null, null),
                    new MockMultipartFile("photo", "p.png", "image/png", new byte[]{1}),
                    new MockMultipartFile("audio", "bad.txt", "text/plain", new byte[]{2})));
        assertEquals(count, challenges.count()); assertEquals(eventCount, events.count()); assertEquals(notificationCount, notifications.count());
        if (Files.exists(UPLOADS)) try (var files = Files.list(UPLOADS)) { assertEquals(0, files.count()); }
    }
    private UUID submit(Category category) throws Exception {
        String body = http.perform(post("/api/challenges").header("Authorization", token(Role.CITIZEN))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input(category))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }
    private Map<String, Object> input(Category category) {
        Map<String, Object> result = new HashMap<>(Map.of("title", "Water issue", "description", "Unsafe supply", "latitude", 20.1,
                "longitude", 77.1, "district", "Test District"));
        if (category != null) result.put("category", category.name()); return result;
    }
    private User user(Role role) { return users.findByPhone("900000000" + role.ordinal()).orElseThrow(); }
    private String token(Role role) {
        User user = user(role); Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("sih-platform").subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("role", role.name()).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    @AfterAll static void removeUploadDirectory() throws Exception {
        if (Files.exists(UPLOADS)) {
            try (var files = Files.list(UPLOADS)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
            Files.deleteIfExists(UPLOADS);
        }
    }
}
