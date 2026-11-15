package in.sih.platform.config;
import in.sih.platform.organization.*;
import in.sih.platform.user.*;
import in.sih.platform.challenge.*;
import in.sih.platform.workflow.*;
import in.sih.platform.notification.*;
import in.sih.platform.project.*;
import in.sih.platform.pledge.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final FacultyProfileRepository faculty;
    private final ChallengeRepository challenges;
    private final ChallengeEventRepository events;
    private final NotificationRepository notifications;
    private final ProjectRepository projects;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final MilestoneRepository milestones;
    private final PledgeRepository pledges;
    public DevDataSeeder(OrganizationRepository organizations, UserRepository users,
            FacultyProfileRepository faculty, ChallengeRepository challenges,
            ChallengeEventRepository events, NotificationRepository notifications, ProjectRepository projects,
            TeamRepository teams, TeamMemberRepository teamMembers, MilestoneRepository milestones, PledgeRepository pledges) {
        this.organizations = organizations; this.users = users; this.faculty = faculty;
        this.challenges = challenges; this.events = events; this.notifications = notifications; this.projects=projects; this.teams=teams;
        this.teamMembers=teamMembers; this.milestones=milestones; this.pledges=pledges;
    }
    @Override @Transactional
    public void run(String... args) {
        if (users.count() != 0) { log.info("Dev seeding skipped: existing users"); return; }
        String[] universityNames={"University 1","Bluecrest University of Applied Futures","Riverbend Institute of Technology","Pinehill School of Public Innovation","Silverleaf University of Science","Eastwind College of Engineering","Stonebridge Institute of Community Studies","Meadowgate University","Cedarfield Institute of Design","Suntrail University of Technology"};
        String[] industryNames={"Industry 1","Mosaic Field Systems","Copperleaf Renewables","OpenCurrent Labs","Kitepath Materials"};
        String[] districts={"Ranchi","Gumla","Dhanbad","East Singhbhum","Bokaro","Hazaribagh","Dumka","Giridih","Deoghar","Palamu"};
        double[][] coordinates={{23.3441,85.3096},{23.1340,84.5400},{23.7957,86.4304},{22.8046,86.2029},{23.6693,86.1511},{23.9925,85.3637},{24.2670,87.2490},{24.1900,86.3000},{24.4850,86.6940},{24.0300,84.0700}};
        List<Organization> universities = new ArrayList<>();
        for (int i=0;i<universityNames.length;i++) universities.add(organization(universityNames[i],OrganizationType.UNIVERSITY,districts[i%10],coordinates[i%10]));
        List<Organization> industries = new ArrayList<>();
        for (int i=0;i<industryNames.length;i++) industries.add(organization(industryNames[i],OrganizationType.INDUSTRY,districts[(i+2)%10],coordinates[(i+2)%10]));
        Organization government = organization("Civic Outcomes Coordination Office (Synthetic)",OrganizationType.GOVERNMENT,"Ranchi",coordinates[0]);
        Map<Role, User> accounts = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            User user = new User(); user.setName("Dev " + role); user.setPhone("900000000" + role.ordinal());
            user.setRole(role); user.setActive(true);
            user.setOrganization(switch (role) {
                case UNIVERSITY -> universities.getFirst(); case INDUSTRY -> industries.getFirst();
                case GOVERNMENT -> government; default -> null;
            });
            accounts.put(role, users.save(user));
        }
        String[] departments={"Water Systems","Community Health","Learning Access","Agroecology","Built Environment"};
        String[] facultyNames={"Asha Sen","Nikhil Das","Mira Kisku","Rohan Ekka","Tara Minz","Kabir Oraon","Leela Toppo","Arun Munda","Sana Lakra","Dev Bedia","Ira Tirkey","Aman Horo"};
        List<FacultyProfile> facultyPool=new ArrayList<>();
        for (int u=0;u<universities.size();u++) for(int j=0;j<3;j++) {
            FacultyProfile profile=new FacultyProfile(); profile.setOrganization(universities.get(u));
            profile.setName("Synthetic Profile " + (u*3+j+1)); profile.setDepartment(departments[(u+j)%departments.length]);
            profile.setBio("Fictional faculty profile focused on locally informed applied research and community partnership.");
            profile.setEmail("profile"+(u*3+j+1)+"@example.invalid"); profile.getExpertiseTags().add(switch((u+j)%7){case 0->"water treatment";case 1->"community health";case 2->"learning access";case 3->"soil resilience";case 4->"renewable energy";case 5->"rural infrastructure";default->"community planning";});
            facultyPool.add(faculty.save(profile));
        }
        String[] titles={"Handpump downtime leaves two hamlets without safe water","Seasonal fever clinic needs reliable cold storage","Secondary students lack a safe evening study space","Small farms report declining soil moisture before sowing","Plastic waste blocks a drainage channel near homes","A footbridge becomes unsafe during monsoon flow","Older residents need easier access to routine checkups","Local seed growers need storage against unseasonal rain","Classrooms need low-cost ventilation improvements","Community pond requires testing and restoration","Bus stop has no lighting for early commuters","Households need guidance on separating organic waste","Primary school needs accessible learning materials","Irrigation channel leaks before the dry season","Street runoff repeatedly floods nearby courtyards","Village health volunteers need basic diagnostic supplies","Market area lacks a safe pedestrian crossing","Solar pump pilot is being monitored through harvest","Clinic outreach route is being evaluated with residents","Community water point repairs are under impact review"};
        String[] descriptions={"Residents report repeated breakdowns of shared handpumps. A local repair roster and water-quality checks could reduce long collection trips.","A small health post loses vaccine storage during power cuts. The team is assessing insulated storage and backup power needs.","Learners share crowded rooms after school and have limited lighting. Residents proposed a supervised, low-energy study corner.","Growers describe longer dry spells and uneven germination. Soil moisture demonstrations and locally selected practices are being explored.","Heavy rain carries packaging into a narrow drain. A maintenance schedule and waste collection point are being discussed with households.","The approach is slippery and narrow during high water. A basic safety assessment and community-prioritized repair plan are needed.","Residents requested periodic checkups closer to home. The proposal includes accessible outreach and referral coordination.","Seed stock is exposed to damp conditions before planting. A shared low-cost storage trial is under consideration.","Warm indoor conditions make afternoon lessons difficult. The school community is comparing shade and ventilation options.","Residents want water testing and a cleanup plan before expanding pond use. Sampling and maintenance responsibilities are being mapped.","Commuters walk before sunrise beside an unlit stop. The group is assessing safe lighting and maintenance options.","Mixed waste is often left near collection points. A household sorting pilot and compost demonstration are being prepared.","Teachers requested locally relevant, reusable learning aids. A small accessible materials pilot is being evaluated.","Water loss along the channel reduces supply at field edges. Farmers are documenting leak locations and repair priorities.","Runoff enters low courtyards during intense rain. Residents are mapping flow paths and testing simple drainage improvements.","Volunteers travel far to replenish basic supplies. A stock and distribution plan is being piloted with the health post.","Pedestrians cross between market stalls and moving traffic. The assessment includes visibility, signage and safer crossing options.","A solar pump pilot has completed installation and is tracking uptime and water access with residents.","A mobile clinic route is operating; the team is reviewing attendance, referral completion and resident feedback.","Repairs to a shared water point are complete and follow-up checks are measuring uptime and user experience."};
        Category[] categories={Category.WATER_SANITATION,Category.HEALTH,Category.EDUCATION,Category.AGRICULTURE,Category.ENVIRONMENT,Category.INFRASTRUCTURE,Category.HEALTH,Category.AGRICULTURE,Category.EDUCATION,Category.WATER_SANITATION,Category.INFRASTRUCTURE,Category.ENVIRONMENT,Category.EDUCATION,Category.AGRICULTURE,Category.INFRASTRUCTURE,Category.HEALTH,Category.OTHER,Category.AGRICULTURE,Category.HEALTH,Category.WATER_SANITATION};
        ChallengeStatus[] states={ChallengeStatus.SUBMITTED,ChallengeStatus.SUBMITTED,ChallengeStatus.SUBMITTED,ChallengeStatus.SUBMITTED,ChallengeStatus.SUBMITTED,ChallengeStatus.VALIDATED,ChallengeStatus.VALIDATED,ChallengeStatus.CATEGORIZED,ChallengeStatus.CATEGORIZED,ChallengeStatus.ASSIGNED,ChallengeStatus.ASSIGNED,ChallengeStatus.PROPOSAL_CREATED,ChallengeStatus.PROPOSAL_CREATED,ChallengeStatus.INDUSTRY_CONNECTED,ChallengeStatus.INDUSTRY_CONNECTED,ChallengeStatus.PROTOTYPE,ChallengeStatus.PILOT,ChallengeStatus.DEPLOYED,ChallengeStatus.IMPACT_EVALUATED,ChallengeStatus.REJECTED};
        List<Challenge> seeded=new ArrayList<>();
        for (int i=0;i<titles.length;i++) {
            Challenge c=new Challenge(); c.setSubmittedBy(accounts.get(Role.CITIZEN));c.setTitle(titles[i]);c.setDescription(descriptions[i]);
            c.setDistrict(districts[i%10]);c.setLatitude(coordinates[i%10][0]);c.setLongitude(coordinates[i%10][1]);c.setCategory(categories[i]);
            c.setSuggestedCategory(categories[i]);c.setClassificationConfidence(.82);c.setAffectedPeople(new int[]{18,240,65,34,110,420,12,75,180,95,260,48,330,52,21,16,600,140,85,9}[i]);
            c.setStatus(states[i]);c.setModerationRequired(i<5 || i==10);c.setModerationReason(i==10?"AI confidence below review threshold":i<5?"Awaiting admin validation":null);
            if(i>=9 && i<=18){c.setAssignedUniversity(universities.get(i%universities.size()));c.setAssignedAt(java.time.Instant.now().minusSeconds(86400*12L));c.setResponseDeadline(java.time.Instant.now().plusSeconds(86400*5L));c.setAssignmentResponse(AssignmentResponse.ACCEPTED);}
            if(i==10){c.setAssignmentResponse(AssignmentResponse.PENDING);}
            if(i==19)c.setModerationReason("Outside the eligible service area after administrative review");
            seeded.add(challenges.save(c));
        }
        // One merged duplicate report remains visible in report totals but points to the retained incident.
        seeded.get(1).setDuplicateOf(seeded.get(0)); seeded.get(1).setAffectedPeople(6);
        for(int i=11;i<=18;i++) {
            Challenge c=seeded.get(i); Project project=new Project(); project.setChallenge(c);project.setUniversity(c.getAssignedUniversity());
            project.setTitle("Community response plan: " + c.getTitle());project.setProposalText("DRAFT — synthetic planning record. The university team will confirm the need with residents, test a small intervention, and report measured results before any expansion.");projects.save(project);
            Team team=new Team();team.setProject(project);team.setName("Community field team");teams.save(team);
            List<FacultyProfile> profiles=facultyPool.stream().filter(f->f.getOrganization().getId().equals(c.getAssignedUniversity().getId())).toList();
            for(int m=0;m<Math.min(2,profiles.size());m++){TeamMember tm=new TeamMember();tm.setTeam(team);tm.setFacultyProfile(profiles.get(m));tm.setRole(m==0?TeamRole.LEAD:TeamRole.MEMBER);teamMembers.save(tm);}
            if(i>=13){Pledge pledge=new Pledge();pledge.setProject(project);pledge.setIndustryOrganization(industries.get((i-13)%industries.size()));pledge.setCreatedBy(accounts.get(Role.INDUSTRY));pledge.setSupportType(i%2==0?SupportType.FUNDING:SupportType.EQUIPMENT);pledge.setDescription("Synthetic in-kind support commitment for the pilot planning record.");if(pledge.getSupportType()==SupportType.FUNDING){pledge.setAmount(new BigDecimal("25000.00"));pledge.setCurrency("INR");}pledges.save(pledge);}
            if(i>=15){Milestone milestone=new Milestone();milestone.setProject(project);milestone.setTitle("Initial community pilot");milestone.setDescription("Synthetic milestone evidence for development dashboards.");milestone.setDueDate(LocalDate.now().plusDays(30));milestone.setStatus(MilestoneStatus.COMPLETED);milestone.setCompletedAt(Instant.now().minusSeconds(86400));milestone.setOutcome("Pilot checkpoint completed; community feedback logged for demonstration.");milestones.save(milestone);}
        }
        for(int i=0;i<seeded.size();i++) {
            Challenge c=seeded.get(i); List<ChallengeStatus> path=path(c.getStatus());
            for(int j=0;j<path.size();j++) {
                ChallengeEvent e=new ChallengeEvent();e.setChallenge(c);e.setFromState(j==0?null:path.get(j-1));e.setToState(path.get(j));
                e.setActor(path.get(j)==ChallengeStatus.PILOT||path.get(j)==ChallengeStatus.DEPLOYED||path.get(j)==ChallengeStatus.IMPACT_EVALUATED?accounts.get(Role.GOVERNMENT):path.get(j)==ChallengeStatus.ASSIGNED||path.get(j)==ChallengeStatus.PROPOSAL_CREATED||path.get(j)==ChallengeStatus.PROTOTYPE?accounts.get(Role.UNIVERSITY):accounts.get(Role.ADMIN));
                if(path.get(j)==ChallengeStatus.REJECTED)e.setReason("Outside the eligible service area after administrative review");
                events.save(e); Notification n=new Notification();n.setRecipient(accounts.get(Role.CITIZEN));n.setChallenge(c);n.setChallengeEvent(e);n.setMessage("Challenge status: "+path.get(j));notifications.save(n);
            }
        }
        log.info("Dev seed counts: universities=10, industry=5, government=1, organizations={}, faculty={}, users={}, challenges={}, events={}, notifications={}",
            organizations.count(), faculty.count(), users.count(), challenges.count(), events.count(), notifications.count());
    }
    private List<ChallengeStatus> path(ChallengeStatus target) {
        List<ChallengeStatus> order=List.of(ChallengeStatus.SUBMITTED,ChallengeStatus.VALIDATED,ChallengeStatus.CATEGORIZED,ChallengeStatus.ASSIGNED,ChallengeStatus.PROPOSAL_CREATED,ChallengeStatus.INDUSTRY_CONNECTED,ChallengeStatus.PROTOTYPE,ChallengeStatus.PILOT,ChallengeStatus.DEPLOYED,ChallengeStatus.IMPACT_EVALUATED);
        if(target==ChallengeStatus.REJECTED)return List.of(ChallengeStatus.SUBMITTED,ChallengeStatus.REJECTED);
        return List.copyOf(order.subList(0,order.indexOf(target)+1));
    }
    private Organization organization(String name, OrganizationType type, String address, double[] point) {
        Organization org = new Organization(); org.setName(name); org.setType(type);
        org.setDescription("Fictional development dataset organization; not a real institution or company."); org.setAddress(address + " (synthetic dataset location)");
        org.setLatitude(point[0]); org.setLongitude(point[1]);
        return organizations.save(org);
    }
}
