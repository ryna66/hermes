package in.sih.platform.config;
import in.sih.platform.organization.*;
import in.sih.platform.user.*;
import in.sih.platform.challenge.*;
import in.sih.platform.workflow.*;
import in.sih.platform.notification.*;
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
    public DevDataSeeder(OrganizationRepository organizations, UserRepository users,
            FacultyProfileRepository faculty, ChallengeRepository challenges,
            ChallengeEventRepository events, NotificationRepository notifications) {
        this.organizations = organizations; this.users = users; this.faculty = faculty;
        this.challenges = challenges; this.events = events; this.notifications = notifications;
    }
    @Override @Transactional
    public void run(String... args) {
        if (users.count() != 0) { log.info("Dev seeding skipped: existing users"); return; }
        List<Organization> universities = new ArrayList<>();
        for (int i = 1; i <= 10; i++) universities.add(organization("University " + i, OrganizationType.UNIVERSITY, i));
        List<Organization> industries = new ArrayList<>();
        for (int i = 1; i <= 5; i++) industries.add(organization("Industry " + i, OrganizationType.INDUSTRY, i));
        Organization government = organization("Government", OrganizationType.GOVERNMENT, 1);
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
        for (Organization university : universities) {
            for (int i = 1; i <= 3; i++) {
                FacultyProfile profile = new FacultyProfile(); profile.setOrganization(university);
                profile.setName(university.getName() + " Faculty " + i); profile.setDepartment("Engineering");
                profile.setBio("Water and infrastructure research"); profile.setEmail("faculty" + i + "@university" + universities.indexOf(university) + ".example");
                profile.getExpertiseTags().add("water treatment"); faculty.save(profile);
            }
        }
        for (int i = 1; i <= 20; i++) {
            Challenge challenge = new Challenge(); challenge.setSubmittedBy(accounts.get(Role.CITIZEN));
            challenge.setTitle("Water supply problem " + i); challenge.setDescription("Unsafe water reported in locality " + i);
            challenge.setLatitude(20 + i * 0.01); challenge.setLongitude(77 + i * 0.01);
            challenge.setStatus(ChallengeStatus.SUBMITTED); challenge.setModerationRequired(true);
            challenge.setModerationReason("Awaiting admin validation"); challenges.save(challenge);
            ChallengeEvent event = new ChallengeEvent(); event.setChallenge(challenge);
            event.setToState(ChallengeStatus.SUBMITTED); event.setActor(accounts.get(Role.CITIZEN)); events.save(event);
            Notification notification = new Notification(); notification.setRecipient(accounts.get(Role.CITIZEN));
            notification.setChallenge(challenge); notification.setChallengeEvent(event);
            notification.setMessage("Challenge submitted"); notifications.save(notification);
        }
        log.info("Dev seed counts: universities=10, industry=5, government=1, organizations={}, faculty={}, users={}, challenges={}, events={}, notifications={}",
            organizations.count(), faculty.count(), users.count(), challenges.count(), events.count(), notifications.count());
    }
    private Organization organization(String name, OrganizationType type, int offset) {
        Organization org = new Organization(); org.setName(name); org.setType(type);
        org.setDescription("Development sample"); org.setAddress("Sample address " + offset);
        org.setLatitude(20 + offset * 0.1); org.setLongitude(77 + offset * 0.1);
        return organizations.save(org);
    }
}
