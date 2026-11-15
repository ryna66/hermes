package in.sih.platform.auth;

import in.sih.platform.user.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final OtpService otps;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration jwtTtl;

    public AuthService(UserRepository users, OtpService otps, JwtEncoder encoder, Clock clock,
            @Value("${auth.jwt-ttl}") Duration jwtTtl) {
        if (jwtTtl.isNegative() || jwtTtl.isZero()) throw new IllegalArgumentException("Invalid JWT TTL");
        this.users = users; this.otps = otps; this.encoder = encoder; this.clock = clock; this.jwtTtl = jwtTtl;
    }

    @Transactional
    public Instant register(String name, String phone) {
        if (users.existsByPhone(phone)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone is already registered");
        User user = new User();
        user.setName(name.strip()); user.setPhone(phone); user.setRole(Role.CITIZEN); user.setActive(true);
        users.saveAndFlush(user);
        return otps.issue(phone);
    }

    public Instant requestOtp(String phone) {
        activeUser(phone);
        return otps.issue(phone);
    }

    public TokenResponse login(String phone, String otp) {
        User user = activeUser(phone);
        otps.verify(phone, otp);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("sih-platform").subject(user.getId().toString())
                .issuedAt(now).expiresAt(expiresAt).claim("role", user.getRole().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", expiresAt);
    }

    private User activeUser(String phone) {
        return users.findByPhone(phone).filter(User::getActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable"));
    }

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) { }
}
