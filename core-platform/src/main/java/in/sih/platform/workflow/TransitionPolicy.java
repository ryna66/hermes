package in.sih.platform.workflow;

import in.sih.platform.challenge.ChallengeStatus;
import in.sih.platform.user.Role;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static in.sih.platform.challenge.ChallengeStatus.*;
import static in.sih.platform.user.Role.*;

/** The complete allowlist; no role has an implicit bypass. */
public final class TransitionPolicy {
    private TransitionPolicy() { }
    public record Edge(ChallengeStatus from, ChallengeStatus to) { }
    private static final Map<Edge, Set<Role>> ALLOWED = Map.ofEntries(
        Map.entry(new Edge(SUBMITTED, VALIDATED), Set.of(ADMIN)),
        Map.entry(new Edge(SUBMITTED, REJECTED), Set.of(ADMIN)),
        Map.entry(new Edge(VALIDATED, CATEGORIZED), Set.of(ADMIN)),
        Map.entry(new Edge(VALIDATED, REJECTED), Set.of(ADMIN)),
        Map.entry(new Edge(CATEGORIZED, ASSIGNED), Set.of(ADMIN)),
        Map.entry(new Edge(CATEGORIZED, REJECTED), Set.of(ADMIN)),
        Map.entry(new Edge(ASSIGNED, CATEGORIZED), Set.of(ADMIN, UNIVERSITY)),
        Map.entry(new Edge(ASSIGNED, PROPOSAL_CREATED), Set.of(UNIVERSITY)),
        Map.entry(new Edge(PROPOSAL_CREATED, INDUSTRY_CONNECTED), Set.of(INDUSTRY)),
        Map.entry(new Edge(INDUSTRY_CONNECTED, PROTOTYPE), Set.of(UNIVERSITY)),
        Map.entry(new Edge(PROTOTYPE, PILOT), Set.of(GOVERNMENT)),
        Map.entry(new Edge(PILOT, DEPLOYED), Set.of(GOVERNMENT)),
        Map.entry(new Edge(DEPLOYED, IMPACT_EVALUATED), Set.of(GOVERNMENT)));

    public static void check(ChallengeStatus from, ChallengeStatus to, Role role) {
        Set<Role> roles = ALLOWED.get(new Edge(from, to));
        if (roles == null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Illegal transition from " + from + " to " + to);
        if (!roles.contains(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Role " + role + " cannot transition from " + from + " to " + to);
    }
}
