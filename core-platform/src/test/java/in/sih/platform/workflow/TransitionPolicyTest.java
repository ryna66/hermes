package in.sih.platform.workflow;

import in.sih.platform.challenge.ChallengeStatus;
import in.sih.platform.user.Role;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class TransitionPolicyTest {
    // Independent specification of all edges and all roles, including terminal states and self transitions.
    static final String[][] TABLE = {
        {"SUBMITTED", "VALIDATED", "ADMIN"}, {"SUBMITTED", "REJECTED", "ADMIN"},
        {"VALIDATED", "CATEGORIZED", "ADMIN"}, {"VALIDATED", "REJECTED", "ADMIN"},
        {"CATEGORIZED", "ASSIGNED", "ADMIN"}, {"CATEGORIZED", "REJECTED", "ADMIN"},
        {"ASSIGNED", "CATEGORIZED", "ADMIN,UNIVERSITY"}, {"ASSIGNED", "PROPOSAL_CREATED", "UNIVERSITY"},
        {"PROPOSAL_CREATED", "INDUSTRY_CONNECTED", "INDUSTRY"}, {"INDUSTRY_CONNECTED", "PROTOTYPE", "UNIVERSITY"},
        {"PROTOTYPE", "PILOT", "GOVERNMENT"}, {"PILOT", "DEPLOYED", "GOVERNMENT"},
        {"DEPLOYED", "IMPACT_EVALUATED", "GOVERNMENT"}
    };
    @TestFactory Stream<DynamicTest> everyStatePairAndRole() {
        return Arrays.stream(ChallengeStatus.values()).flatMap(from -> Arrays.stream(ChallengeStatus.values())
            .flatMap(to -> Arrays.stream(Role.values()).map(role -> DynamicTest.dynamicTest(from + " -> " + to + " as " + role, () -> {
                Optional<String[]> edge = Arrays.stream(TABLE).filter(row -> row[0].equals(from.name()) && row[1].equals(to.name())).findFirst();
                if (edge.isPresent() && Arrays.asList(edge.get()[2].split(",")).contains(role.name()))
                    assertDoesNotThrow(() -> TransitionPolicy.check(from, to, role));
                else {
                    var error = assertThrows(ResponseStatusException.class, () -> TransitionPolicy.check(from, to, role));
                    assertEquals(edge.isPresent() ? 403 : 409, error.getStatusCode().value());
                    assertTrue(error.getReason().contains(from.name())); assertTrue(error.getReason().contains(to.name()));
                }
            }))));
    }
}
