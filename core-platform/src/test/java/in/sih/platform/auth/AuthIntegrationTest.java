package in.sih.platform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.sih.platform.user.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.TestConfig.class)
class AuthIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TestClock clock;
    @Autowired CapturingDelivery delivery;

    @BeforeEach void resetTime() { clock.now = Instant.parse("2026-11-15T10:00:00Z"); }

    @ParameterizedTest @EnumSource(Role.class)
    void seededUsersCanLogIn(Role role) throws Exception {
        String token = login(role);
        http.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.id").value(users.findByPhone(phone(role)).orElseThrow().getId().toString()))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @ParameterizedTest @EnumSource(Role.class)
    void permissionMatrixHasNoImplicitAdminBypass(Role caller) throws Exception {
        String token = login(caller);
        for (Role required : Role.values()) {
            http.perform(get("/api/auth-test/" + required.name().toLowerCase(Locale.ROOT))
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().is(caller == required ? 200 : 403));
        }
        http.perform(post("/api/auth-test/transitions").header("Authorization", "Bearer " + token))
                .andExpect(status().is(Set.of(Role.ADMIN, Role.UNIVERSITY, Role.GOVERNMENT).contains(caller) ? 200 : 403));
    }

    @Test void anonymousRequestsAreRejected() throws Exception {
        http.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        for (Role role : Role.values()) {
            http.perform(get("/api/auth-test/" + role.name().toLowerCase(Locale.ROOT))).andExpect(status().isUnauthorized());
        }
    }

    @Test void wrongOtpDoesNotAuthenticate() throws Exception {
        String phone = phone(Role.CITIZEN);
        String correct = requestOtp(phone);
        verify(phone, wrong(correct), 401);
        verify(phone, correct, 200);
    }

    @Test void expiredOtpIsRejectedAtExpiry() throws Exception {
        String phone = phone(Role.CITIZEN);
        String otp = requestOtp(phone);
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        verify(phone, otp, 401);
    }

    @Test void successfulOtpIsSingleUse() throws Exception {
        String phone = phone(Role.CITIZEN);
        String otp = requestOtp(phone);
        verify(phone, otp, 200);
        verify(phone, otp, 401);
    }

    @Test void fiveWrongAttemptsInvalidateOtp() throws Exception {
        String phone = phone(Role.CITIZEN);
        String otp = requestOtp(phone);
        for (int attempt = 0; attempt < 5; attempt++) verify(phone, wrong(otp), 401);
        verify(phone, otp, 401);
    }

    @Test void reissuingOtpReplacesPreviousCode() throws Exception {
        String phone = phone(Role.CITIZEN);
        requestOtp(phone);
        clock.now = clock.now.plusSeconds(240);
        String replacement = requestOtp(phone);
        clock.now = clock.now.plusSeconds(60);
        verify(phone, replacement, 200);
    }

    @Test void invalidAndExpiredJwtAreRejected() throws Exception {
        String token = login(Role.CITIZEN);
        String[] parts = token.split("\\.");
        String signature = parts[2];
        String tampered = parts[0] + "." + parts[1] + "." + (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        http.perform(get("/api/users/me").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
        http.perform(get("/api/users/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(1);
        http.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void registrationCreatesOnlyCitizenAndStartsOtpLogin() throws Exception {
        String phone = "9876543210";
        try {
            http.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("name", "New citizen", "phone", phone, "role", "ADMIN"))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").exists())
                    .andExpect(jsonPath("$.otp").doesNotExist());
            assertEquals(Role.CITIZEN, users.findByPhone(phone).orElseThrow().getRole());
            verify(phone, delivery.codes.get(phone), 200);
            http.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("name", "Duplicate", "phone", phone))))
                    .andExpect(status().isConflict());
        } finally {
            users.findByPhone(phone).ifPresent(users::delete);
        }
    }

    @Test void invalidRegistrationAndUnknownAccountAreRejected() throws Exception {
        http.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \",\"phone\":\"bad\"}")).andExpect(status().isBadRequest());
        http.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"9999999999\"}")).andExpect(status().isUnauthorized());
    }

    @Test void inactiveAccountCannotRequestOrVerifyOtp() throws Exception {
        String phone = phone(Role.INDUSTRY);
        String otp = requestOtp(phone);
        User user = users.findByPhone(phone).orElseThrow();
        try {
            user.setActive(false); users.saveAndFlush(user);
            http.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("phone", phone)))).andExpect(status().isUnauthorized());
            verify(phone, otp, 401);
        } finally {
            user.setActive(true); users.saveAndFlush(user);
        }
    }

    private String phone(Role role) { return "900000000" + role.ordinal(); }
    private String wrong(String otp) { return otp.equals("000000") ? "111111" : "000000"; }
    private String requestOtp(String phone) throws Exception {
        http.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.otp").doesNotExist());
        return delivery.codes.get(phone);
    }
    private String login(Role role) throws Exception {
        String phone = phone(role);
        return verify(phone, requestOtp(phone), 200);
    }
    private String verify(String phone, String otp, int expectedStatus) throws Exception {
        String body = http.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone, "otp", otp))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        if (expectedStatus != 200) return null;
        var response = json.readTree(body);
        assertEquals("Bearer", response.get("tokenType").asText());
        assertTrue(response.hasNonNull("expiresAt"));
        assertTrue(response.hasNonNull("accessToken"));
        return response.get("accessToken").asText();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
        @Bean @Primary CapturingDelivery capturingDelivery() { return new CapturingDelivery(); }
        @Bean RoleEndpoints roleEndpoints() { return new RoleEndpoints(); }
    }
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-15T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
    static class CapturingDelivery implements OtpDelivery {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        @Override public void send(String phone, String otp) { codes.put(phone, otp); }
    }
    @RestController
    @RequestMapping("/api/auth-test")
    static class RoleEndpoints {
        @GetMapping("/citizen") @PreAuthorize("hasRole('CITIZEN')")
        public String citizen() { return "ok"; }
        @GetMapping("/admin") @PreAuthorize("hasRole('ADMIN')")
        public String admin() { return "ok"; }
        @GetMapping("/university") @PreAuthorize("hasRole('UNIVERSITY')")
        public String university() { return "ok"; }
        @GetMapping("/industry") @PreAuthorize("hasRole('INDUSTRY')")
        public String industry() { return "ok"; }
        @GetMapping("/government") @PreAuthorize("hasRole('GOVERNMENT')")
        public String government() { return "ok"; }
        @PostMapping("/transitions") @PreAuthorize("hasAnyRole('ADMIN', 'UNIVERSITY', 'GOVERNMENT')")
        public String transitions() { return "ok"; }
    }
}
