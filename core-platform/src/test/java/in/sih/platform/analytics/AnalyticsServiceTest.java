package in.sih.platform.analytics;
import in.sih.platform.challenge.*;
import in.sih.platform.project.*;
import in.sih.platform.pledge.*;
import in.sih.platform.workflow.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AnalyticsServiceTest {
    ChallengeEvent event(ChallengeStatus from,ChallengeStatus to,int seconds){var e=mock(ChallengeEvent.class);when(e.getFromState()).thenReturn(from);when(e.getToState()).thenReturn(to);when(e.getCreatedAt()).thenReturn(Instant.EPOCH.plusSeconds(seconds));return e;}
    @Test void averagesCompletedVisitsAndExcludesSameStateFeedbackAndCurrentStage(){
        var challenges=mock(ChallengeRepository.class);var projects=mock(ProjectRepository.class);var pledges=mock(PledgeRepository.class);var events=mock(ChallengeEventRepository.class);
        Challenge c=mock(Challenge.class);UUID id=UUID.randomUUID();when(c.getId()).thenReturn(id);when(c.getStatus()).thenReturn(ChallengeStatus.DEPLOYED);when(c.getDistrict()).thenReturn("District");when(c.getCategory()).thenReturn(Category.HEALTH);
        when(challenges.findAll()).thenReturn(List.of(c));when(pledges.findAll()).thenReturn(List.of());
        var history=List.of(event(null,ChallengeStatus.SUBMITTED,0),event(ChallengeStatus.SUBMITTED,ChallengeStatus.VALIDATED,10),event(ChallengeStatus.VALIDATED,ChallengeStatus.CATEGORIZED,30),event(ChallengeStatus.CATEGORIZED,ChallengeStatus.ASSIGNED,60),event(ChallengeStatus.ASSIGNED,ChallengeStatus.CATEGORIZED,100),event(ChallengeStatus.CATEGORIZED,ChallengeStatus.ASSIGNED,110),event(ChallengeStatus.ASSIGNED,ChallengeStatus.ASSIGNED,120));
        when(events.findByChallengeIdOrderByCreatedAtAsc(id)).thenReturn(history);
        var d=new AnalyticsService(challenges,projects,pledges,events).dashboard();
        assertEquals(10.,d.averageStageSeconds().get("SUBMITTED"));assertEquals(20.,d.averageStageSeconds().get("VALIDATED"));assertEquals(20.,d.averageStageSeconds().get("CATEGORIZED"));assertEquals(2L,d.stageSamples().get("CATEGORIZED"));assertEquals(40.,d.averageStageSeconds().get("ASSIGNED"));assertEquals(1L,d.stageSamples().get("ASSIGNED"));assertEquals(1,d.resolved());assertEquals(0,d.open());assertEquals(1L,d.byDistrict().get("District"));
    }
}
