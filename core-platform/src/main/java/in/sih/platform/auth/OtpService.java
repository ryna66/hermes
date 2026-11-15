package in.sih.platform.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OtpService {
    private final Map<String, PendingOtp> pending = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration ttl;
    private final int maxAttempts;
    private final OtpDelivery delivery;
    private record PendingOtp(String code, Instant expiresAt, int attempts) { }

    public OtpService(Clock clock, OtpDelivery delivery, @Value("${auth.otp-ttl}") Duration ttl,
            @Value("${auth.otp-max-attempts}") int maxAttempts) {
        if (ttl.isNegative() || ttl.isZero() || maxAttempts < 1) throw new IllegalArgumentException("Invalid OTP settings");
        this.clock = clock; this.delivery = delivery; this.ttl = ttl; this.maxAttempts = maxAttempts;
    }

    public synchronized Instant issue(String phone) {
        Instant now = clock.instant();
        pending.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expiresAt = now.plus(ttl);
        pending.remove(phone);
        delivery.send(phone, code);
        pending.put(phone, new PendingOtp(code, expiresAt, 0));
        return expiresAt;
    }

    public synchronized void verify(String phone, String code) {
        PendingOtp otp = pending.get(phone);
        if (otp == null || !clock.instant().isBefore(otp.expiresAt())) {
            pending.remove(phone);
            throw invalidOtp();
        }
        if (!MessageDigest.isEqual(otp.code().getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            if (otp.attempts() + 1 >= maxAttempts) pending.remove(phone);
            else pending.put(phone, new PendingOtp(otp.code(), otp.expiresAt(), otp.attempts() + 1));
            throw invalidOtp();
        }
        pending.remove(phone);
    }

    private ResponseStatusException invalidOtp() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP");
    }
}
