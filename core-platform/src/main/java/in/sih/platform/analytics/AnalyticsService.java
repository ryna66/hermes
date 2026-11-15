package in.sih.platform.analytics;
import in.sih.platform.challenge.*;
import in.sih.platform.project.*;
import in.sih.platform.pledge.*;
import in.sih.platform.workflow.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class AnalyticsService {
    private final ChallengeRepository challenges;private final ProjectRepository projects;private final PledgeRepository pledges;private final ChallengeEventRepository events;
    public AnalyticsService(ChallengeRepository challenges,ProjectRepository projects,PledgeRepository pledges,ChallengeEventRepository events){this.challenges=challenges;this.projects=projects;this.pledges=pledges;this.events=events;}
    public record Dashboard(Map<String,Long> byStatus,Map<String,Long> byCategory,Map<String,Long> byDistrict,long open,long resolved,long rejected,
        Map<String,Long> universityParticipation,Map<String,Long> industryPledges,Map<String,Long> pledgesByType,Map<String,BigDecimal> fundingByCurrency,
        Map<String,Double> averageStageSeconds,Map<String,Long> stageSamples) {}
    private void count(Map<String,Long> map,String key){map.merge(key,1L,Long::sum);}
    public Dashboard dashboard(){
        Map<String,Long> status=new TreeMap<>(),category=new TreeMap<>(),district=new TreeMap<>(),universities=new TreeMap<>(),industry=new TreeMap<>(),types=new TreeMap<>(),samples=new TreeMap<>();
        for(var s:ChallengeStatus.values())status.put(s.name(),0L);
        Map<String,BigDecimal> funding=new TreeMap<>();Map<String,Double> durations=new TreeMap<>();long open=0,resolved=0,rejected=0;
        for(var c:challenges.findAll()){
            count(status,c.getStatus().name());count(category,c.getCategory()==null?"UNCATEGORIZED":c.getCategory().name());count(district,c.getDistrict()==null?"UNKNOWN":c.getDistrict());
            if(c.getStatus()==ChallengeStatus.REJECTED)rejected++;else if(c.getStatus()==ChallengeStatus.DEPLOYED||c.getStatus()==ChallengeStatus.IMPACT_EVALUATED)resolved++;else open++;
            if(c.getAssignedUniversity()!=null)count(universities,c.getAssignedUniversity().getId().toString());
            var history=events.findByChallengeIdOrderByCreatedAtAsc(c.getId());
            ChallengeEvent entry=null;
            for(var e:history){
                if(e.getFromState()==e.getToState())continue;
                if(entry!=null && e.getFromState()==entry.getToState()){
                    double seconds=Duration.between(entry.getCreatedAt(),e.getCreatedAt()).toNanos()/1_000_000_000.0;
                    if(seconds>=0){durations.merge(entry.getToState().name(),seconds,Double::sum);count(samples,entry.getToState().name());}
                }
                entry=e;
            }
        }
        for(var p:pledges.findAll()){count(industry,p.getIndustryOrganization().getId().toString());count(types,p.getSupportType().name());if(p.getSupportType()==SupportType.FUNDING)funding.merge(p.getCurrency(),p.getAmount(),BigDecimal::add);}
        durations.replaceAll((k,v)->v/samples.get(k));
        return new Dashboard(status,category,district,open,resolved,rejected,universities,industry,types,funding,durations,samples);
    }
}
