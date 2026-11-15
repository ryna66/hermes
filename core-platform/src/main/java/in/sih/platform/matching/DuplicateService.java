package in.sih.platform.matching;

import in.sih.platform.challenge.*;
import in.sih.platform.user.*;
import in.sih.platform.workflow.WorkflowService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DuplicateService {
    private final ChallengeRepository challenges;
    private final VectorSupport vectors;
    private final GeoService geo;
    private final double threshold, radius;
    public DuplicateService(ChallengeRepository challenges, VectorSupport vectors, GeoService geo,
            @Value("${matching.duplicate-similarity-threshold:0.85}") double threshold,
            @Value("${matching.duplicate-radius-km:10}") double radius) {
        this.challenges=challenges; this.vectors=vectors; this.geo=geo; this.threshold=threshold; this.radius=radius;
    }
    public record Candidate(UUID id, String title, double similarity, double distanceKm) { }
    public List<Candidate> candidates(Challenge c) {
        return challenges.findAll().stream().filter(o -> !o.getId().equals(c.getId()) && o.getDuplicateOf()==null &&
                o.getStatus()!=ChallengeStatus.REJECTED && !o.getCreatedAt().isAfter(c.getCreatedAt()))
            .map(o -> {
                Double score=vectors.compare(c,o);
                double distance=geo.distanceKm(c.getLatitude(),c.getLongitude(),o.getLatitude(),o.getLongitude());
                return score!=null && score>=threshold && distance<=radius ? new Candidate(o.getId(),o.getTitle(),score,distance) : null;
            }).filter(Objects::nonNull).sorted(Comparator.comparingDouble(Candidate::similarity).reversed().thenComparing(Candidate::id)).toList();
    }
    public void flag(Challenge c) { c.setDuplicateCandidate(!candidates(c).isEmpty()); }
    public Challenge merge(Challenge duplicate, Challenge original, User actor) {
        WorkflowService.requireRole(actor,Role.ADMIN);
        WorkflowService.require(!duplicate.getId().equals(original.getId()),"Cannot merge a challenge into itself");
        if (duplicate.getDuplicateOf()!=null) {
            WorkflowService.require(duplicate.getDuplicateOf().getId().equals(original.getId()),"Already merged elsewhere"); return duplicate;
        }
        WorkflowService.require(original.getDuplicateOf()==null,"Original is already a duplicate");
        WorkflowService.require(duplicate.getStatus()==ChallengeStatus.SUBMITTED || duplicate.getStatus()==ChallengeStatus.VALIDATED || duplicate.getStatus()==ChallengeStatus.CATEGORIZED,"Only unallocated challenges can be merged");
        WorkflowService.require(challenges.findAll().stream().noneMatch(c -> c.getDuplicateOf()!=null && c.getDuplicateOf().getId().equals(duplicate.getId())),"Cannot merge an original with linked duplicates");
        long total=(long)original.getAffectedPeople()+duplicate.getAffectedPeople();
        WorkflowService.require(total<=Integer.MAX_VALUE,"Affected people total exceeds supported limit");
        original.setAffectedPeople((int)total);
        duplicate.setDuplicateOf(original); duplicate.setDuplicateCandidate(false);
        return duplicate;
    }
}
