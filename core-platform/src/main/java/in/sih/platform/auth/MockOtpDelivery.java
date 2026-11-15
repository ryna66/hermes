package in.sih.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod")
public class MockOtpDelivery implements OtpDelivery {
    private static final Logger log = LoggerFactory.getLogger(MockOtpDelivery.class);
    @Override public void send(String phone, String otp) {
        log.info("Development OTP for {}: {}", phone, otp);
    }
}
