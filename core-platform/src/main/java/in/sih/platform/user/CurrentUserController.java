package in.sih.platform.user;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CurrentUserController {
    private final UserRepository users;
    public CurrentUserController(UserRepository users) { this.users = users; }
    public record Account(UUID id, String name, Role role) { }

    @GetMapping("/api/users/me")
    @PreAuthorize("isAuthenticated()")
    public Account me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.findById(UUID.fromString(jwt.getSubject())).filter(User::getActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable"));
        return new Account(user.getId(), user.getName(), user.getRole());
    }
}
