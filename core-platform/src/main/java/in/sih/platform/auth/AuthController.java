package in.sih.platform.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record Registration(@NotBlank @Size(max = 200) String name,
            @NotNull @Pattern(regexp = "\\+?[0-9]{10,15}") String phone) { }
    public record OtpRequest(@NotNull @Pattern(regexp = "\\+?[0-9]{10,15}") String phone) { }
    public record OtpVerification(@NotNull @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
            @NotNull @Pattern(regexp = "[0-9]{6}") String otp) { }
    public record OtpResponse(Instant expiresAt) { }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public OtpResponse register(@Valid @RequestBody Registration request) {
        return new OtpResponse(auth.register(request.name(), request.phone()));
    }
    @PostMapping("/otp/request")
    public OtpResponse request(@Valid @RequestBody OtpRequest request) {
        return new OtpResponse(auth.requestOtp(request.phone()));
    }
    @PostMapping("/otp/verify")
    public AuthService.TokenResponse verify(@Valid @RequestBody OtpVerification request) {
        return auth.login(request.phone(), request.otp());
    }
}
