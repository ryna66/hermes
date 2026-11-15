package in.sih.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PageControllerTest {
 @Autowired MockMvc http;
 @Test void publicPagesLoadAndExposeActions() throws Exception {
  http.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/landing.html"));
  http.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/login.html"));
  http.perform(get("/explore")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/explore.html"));
  http.perform(get("/challenges/example")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/challenge.html"));
  http.perform(get("/universities/example/departments")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/departments.html"));
 }
 @Test @WithMockUser(roles="CITIZEN") void citizenPagesLoadAndExposeActions() throws Exception {
  http.perform(get("/submit")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/submit.html"));
  http.perform(get("/my-challenges")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/my-challenges.html"));
 }
 @Test @WithMockUser(roles="ADMIN") void adminPagesLoadAndExposeActions() throws Exception {
  http.perform(get("/admin/validation")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/validation.html"));
  http.perform(get("/admin/allocation")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/allocation.html"));
 }
 @Test @WithMockUser(roles="UNIVERSITY") void universityInboxLoads() throws Exception { http.perform(get("/university/inbox")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/inbox.html")); }
 @Test @WithMockUser(roles="UNIVERSITY") void projectWorkspaceLoadsForUniversity() throws Exception { http.perform(get("/projects/example")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/project.html")); }
 @Test @WithMockUser(roles="INDUSTRY") void industryPageLoadsAndExposesActions() throws Exception { http.perform(get("/industry")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/industry.html")); }
 @Test @WithMockUser(roles="GOVERNMENT") void governmentPageLoadsAndExposesAction() throws Exception { http.perform(get("/government")).andExpect(status().isOk()).andExpect(forwardedUrl("/pages/government.html")); }
 @Test @WithMockUser(roles="CITIZEN") void privilegedPagesAreBlocked() throws Exception {
  http.perform(get("/admin/validation")).andExpect(status().isForbidden()); http.perform(get("/admin/allocation")).andExpect(status().isForbidden());
  http.perform(get("/university/inbox")).andExpect(status().isForbidden()); http.perform(get("/industry")).andExpect(status().isForbidden()); http.perform(get("/government")).andExpect(status().isForbidden());
 }
}
