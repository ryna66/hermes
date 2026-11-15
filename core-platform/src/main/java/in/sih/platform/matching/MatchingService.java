package in.sih.platform.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.sih.platform.ai.*;
import in.sih.platform.challenge.*;
import in.sih.platform.organization.*;
import in.sih.platform.user.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class MatchingService {
    private final OrganizationRepository organizations;
    private final FacultyProfileRepository faculty;
    private final ChallengeRepository challenges;
    private final AssignmentRecordRepository assignments;
    private final AiClient ai;
    private final ObjectMapper json;
    private final VectorSupport vectors;
    private final GeoService geo;
    private final double confidence;
    public MatchingService(OrganizationRepository organizations, FacultyProfileRepository faculty, ChallengeRepository challenges,
            AssignmentRecordRepository assignments, AiClient ai, ObjectMapper json, VectorSupport vectors, GeoService geo,
            @Value("${ai.confidence-threshold:0.7}") double confidence) {
        this.organizations=organizations; this.faculty=faculty; this.challenges=challenges; this.assignments=assignments;
        this.ai=ai; this.json=json; this.vectors=vectors; this.geo=geo; this.confidence=confidence;
    }
    public record FacultyMatch(UUID id, String name, double score, List<String> reasons) { }
    public record UniversityMatch(UUID id, String name, double score, List<String> reasons, List<FacultyMatch> faculty) { }
    // Weights: semantic .50, category expertise .20, distance .15, capacity .10, acceptance .05.
    // Missing/incompatible embeddings contribute zero; rules remain available during AI outages.
    public List<UniversityMatch> rank(Challenge c) {
        var profiles=faculty.findAll(); var active=challenges.findAll(); var history=assignments.findAll();
        var result=new ArrayList<UniversityMatch>();
        boolean[] aiAvailable={c.getEmbeddingText()!=null};
        for (Organization u: organizations.findAll()) {
            if (u.getType()!=OrganizationType.UNIVERSITY) continue;
            double distance=geo.distanceKm(c.getLatitude(),c.getLongitude(),u.getLatitude(),u.getLongitude());
            long workload=active.stream().filter(a -> a.getAssignedUniversity()!=null && a.getAssignedUniversity().getId().equals(u.getId()) &&
                    a.getStatus()!=ChallengeStatus.IMPACT_EVALUATED && a.getStatus()!=ChallengeStatus.REJECTED).count();
            var records=history.stream().filter(a -> a.getUniversityId().equals(u.getId()) && !a.getResponse().equals("PENDING") && !a.getResponse().equals("RECALLED")).toList();
            double rate=records.isEmpty()?0.5:(double)records.stream().filter(a -> a.getResponse().equals("ACCEPTED")).count()/records.size();
            double rules=.15/(1+distance/100)+.10/(1+workload)+.05*rate;
            var people=new ArrayList<FacultyMatch>();
            for (FacultyProfile f:profiles) {
                if (!f.getOrganization().getId().equals(u.getId())) continue;
                double semantic=0; boolean embedded=false;
                if (aiAvailable[0]) try {
                    var e=ai.embed(f.getDepartment()+" "+f.getBio()+" "+String.join(" ",new TreeSet<>(f.getExpertiseTags())));
                    HttpAiClient.validate(e);
                    Double value=e.confidence()>=confidence ? vectors.compare(c,json.writeValueAsString(e.embedding()),e.model()):null;
                    if(value!=null) { semantic=Math.max(0,value); embedded=true; }
                } catch(Exception ex) { aiAvailable[0]=false; }
                Category category=c.getCategory()!=null?c.getCategory():c.getSuggestedCategory();
                String tags=String.join(" ",f.getExpertiseTags()).toLowerCase(Locale.ROOT);
                boolean overlap=category!=null && Arrays.stream(category.name().toLowerCase(Locale.ROOT).split("_")).anyMatch(tags::contains);
                double score=.50*semantic+.20*(overlap?1:0)+rules;
                people.add(new FacultyMatch(f.getId(),f.getName(),score,List.of(embedded?"Embedding similarity: "+semantic:"Embedding unavailable or incompatible; rules used", "Category expertise overlap: "+overlap)));
            }
            people.sort(Comparator.comparingDouble(FacultyMatch::score).reversed().thenComparing(FacultyMatch::id));
            result.add(new UniversityMatch(u.getId(),u.getName(),people.isEmpty()?rules:people.getFirst().score(),
                List.of("Distance: "+distance+" km", "Current workload: "+workload, "Past acceptance rate: "+rate+(records.isEmpty()?" (neutral default; no history)":""),
                        "Score weights: embedding 50%, expertise 20%, proximity 15%, capacity 10%, acceptance 5%"),people));
        }
        result.sort(Comparator.comparingDouble(UniversityMatch::score).reversed().thenComparing(UniversityMatch::id)); return result;
    }
}
