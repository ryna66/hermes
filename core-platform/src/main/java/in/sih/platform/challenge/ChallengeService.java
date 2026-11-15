package in.sih.platform.challenge;

import in.sih.platform.user.*;
import in.sih.platform.workflow.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ChallengeService {
    private final ChallengeRepository challenges;
    private final UserRepository users;
    private final WorkflowService workflow;
    private final LocalAttachmentStorage storage;
    public ChallengeService(ChallengeRepository challenges, UserRepository users, WorkflowService workflow, LocalAttachmentStorage storage) {
        this.challenges = challenges; this.users = users; this.workflow = workflow; this.storage = storage;
    }
    public User actor(UUID id) {
        return users.findById(id).filter(User::getActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable"));
    }
    public record View(UUID id, String title, String description, double latitude, double longitude,
                       String district, Category category, int affectedPeople, ChallengeStatus status,
                       boolean hasPhoto, boolean hasAudio, Instant fixedConfirmedAt, Instant createdAt) {
        public static View of(Challenge c) {
            return new View(c.getId(), c.getTitle(), c.getDescription(), c.getLatitude(), c.getLongitude(),
                    c.getDistrict(), c.getCategory(), c.getAffectedPeople(), c.getStatus(),
                    c.getPhotoPath() != null, c.getAudioPath() != null, c.getFixedConfirmedAt(), c.getCreatedAt());
        }
    }
    public View submit(UUID userId, ChallengeInput input, MultipartFile photo, MultipartFile audio) {
        User actor = actor(userId); WorkflowService.requireRole(actor, Role.CITIZEN);
        WorkflowService.require(Double.isFinite(input.latitude()) && Double.isFinite(input.longitude()), "Coordinates must be finite");
        Challenge c = new Challenge(); c.setSubmittedBy(actor); c.setTitle(input.title().trim());
        c.setDescription(input.description().trim()); c.setLatitude(input.latitude()); c.setLongitude(input.longitude());
        c.setDistrict(input.district().trim()); c.setCategory(input.category());
        c.setAffectedPeople(input.affectedPeople() == null ? 1 : input.affectedPeople());
        c.setStatus(ChallengeStatus.SUBMITTED); c.setModerationRequired(true);
        c.setModerationReason("Awaiting manual moderation");
        c.setPhotoPath(storage.store(photo, "photo")); c.setAudioPath(storage.store(audio, "audio"));
        challenges.save(c); workflow.submitted(c, actor);
        return View.of(c);
    }
    @Transactional(readOnly = true)
    public Page<View> explore(String district, Category category, ChallengeStatus status, int page, int size) {
        WorkflowService.require(page >= 0 && size >= 1 && size <= 100, "Page must be nonnegative and size between 1 and 100");
        Specification<Challenge> filter = (root, query, cb) -> cb.conjunction();
        if (district != null && !district.isBlank()) filter = filter.and((r, q, cb) -> cb.equal(cb.lower(r.get("district")), district.trim().toLowerCase(Locale.ROOT)));
        if (category != null) filter = filter.and((r, q, cb) -> cb.equal(r.get("category"), category));
        if (status != null) filter = filter.and((r, q, cb) -> cb.equal(r.get("status"), status));
        return challenges.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))).map(View::of);
    }
    @Transactional(readOnly = true)
    public View detail(UUID id) { return View.of(workflow.requireChallenge(id)); }
}
