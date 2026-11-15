package in.sih.platform;
import in.sih.platform.challenge.ChallengeRepository;
import in.sih.platform.organization.OrganizationRepository;
import in.sih.platform.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc
class SkeletonIntegrationTest {
    @Autowired MockMvc http;
    @Autowired UserRepository users;
    @Autowired FacultyProfileRepository faculty;
    @Autowired OrganizationRepository organizations;
    @Autowired ChallengeRepository challenges;
    @Test void migratedSeededApplicationServesDocumentation() throws Exception {
        assertEquals(5, users.count()); assertEquals(30, faculty.count());
        assertEquals(16, organizations.count()); assertEquals(20, challenges.count());
        http.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        http.perform(get("/v3/api-docs")).andExpect(status().isOk());
        http.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
