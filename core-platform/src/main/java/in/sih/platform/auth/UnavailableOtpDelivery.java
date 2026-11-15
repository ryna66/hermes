package in.sih.platform.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!dev | prod")
public class UnavailableOtpDelivery implements OtpDelivery {
    @Override public void send(String phone, String otp) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OTP delivery is not configured");
    }
}
