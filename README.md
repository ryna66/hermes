# SIH26043

Milestone 6: Java 21 / Maven 3.9+, Spring Boot 3.3.5 with OTP/JWT authentication, challenge intake, lifecycle rules, AI enrichment, duplicate review and university/faculty matching.

Run from the repository root:

```sh
mvn -f core-platform/pom.xml clean verify
mvn -f core-platform/pom.xml spring-boot:run
```

Use a Java 21 `JAVA_HOME` (macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Default profile is `dev`: Flyway creates all tables, Hibernate validates mappings, then the dev-only seeder logs 10 universities, 30 faculty, 20 challenges, 5 industry organizations, one government organization and five users (one per role). Submission events and notifications accompany seeded challenges. H2 data disappears when the JVM stops.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Repositories use JPA; schema changes belong in new Flyway migrations. Lifecycle and assignment ownership rules are enforced by the workflow service. Project/team and pledge APIs arrive in milestone 7.

## Switching to a permanent database

1. Start Docker Desktop (or another Docker daemon).
2. From the repository root, set a local password and start the optional database:

   ```sh
   export DB_PASSWORD='replace-with-a-local-password'
   docker compose --profile database up -d --wait postgres
   ```

3. Stop the dev application, then start against PostgreSQL using these environment variables:

   ```sh
   export SPRING_PROFILES_ACTIVE=prod
   export DB_URL='jdbc:postgresql://localhost:5432/sih'
   export DB_USERNAME=sih
   export JWT_SECRET='replace-with-a-random-secret-of-at-least-32-bytes'
   # Keep DB_PASSWORD from step 2 in this shell.
   mvn -f core-platform/pom.xml spring-boot:run
   ```

   Flyway applies the same V1 and V2 SQL automatically; Hibernate validates it. The prod profile never seeds sample data. Check startup for migration success and open `/actuator/health` and Swagger UI. Prod requires all three DB variables and JWT_SECRET, with no credential defaults. OTP delivery is not configured in prod; auth OTP requests return 503 until a real delivery implementation is supplied.

4. Restart the app with the same environment to retain data. `docker compose --profile database down` stops PostgreSQL while preserving its named volume. Do not remove the volume if you need the data.
5. To return to temporary H2, stop the app, run `unset SPRING_PROFILES_ACTIVE DB_URL DB_USERNAME DB_PASSWORD`, then run the default startup command.

The SQL uses UUID, standard text/numeric/boolean/date/timestamp types and foreign keys; no vendor-specific extensions. PostgreSQL execution must be verified with a running daemon; a Docker client alone is insufficient.

## Authentication

API clients use `Authorization: Bearer <accessToken>`; no session or cookie authentication is enabled yet.

- `POST /api/auth/register` with `{"name":"Example","phone":"9876543210"}` creates a CITIZEN and starts OTP verification. Client-supplied roles never grant privileges. Duplicate phones return 409.
- `POST /api/auth/otp/request` with `{"phone":"9000000000"}` starts login for an existing active user. Read the random six-digit OTP from the dev console.
- `POST /api/auth/otp/verify` with `{"phone":"9000000000","otp":"<six digits>"}` returns `accessToken`, `tokenType` (`Bearer`) and `expiresAt`.
- `GET /api/users/me` returns the authenticated user's ID, name and role.

Seeded phones: CITIZEN `9000000000`, ADMIN `9000000001`, UNIVERSITY `9000000002`, INDUSTRY `9000000003`, GOVERNMENT `9000000004`. Each logs in through the same OTP flow.

OTPs expire after `OTP_TTL` (default `PT5M`), are single-use and allow `OTP_MAX_ATTEMPTS` (default 5) wrong attempts. Requesting another OTP replaces the previous one. Pending OTPs are held in memory and disappear on restart. OTPs are printed only in dev. JWTs use HS256, expire after `JWT_TTL` (default `PT1H`) and use an ephemeral dev key unless `JWT_SECRET` is set. Prod requires a secret of at least 32 UTF-8 bytes. Restarting with an ephemeral key invalidates existing tokens.

Run auth integration tests with `mvn -f core-platform/pom.xml -Dtest=AuthIntegrationTest test`. Tests use MockMvc, the real seeder, actual OTP verification and signed JWTs, plus test-only role endpoints; they do not implement challenge logic.

## Challenge intake and workflow

`POST /api/challenges` requires a citizen token. Send JSON such as:

```json
{"title":"Unsafe water","description":"Village taps supply dirty water","latitude":20.1,"longitude":77.1,"district":"Example","category":"WATER_SANITATION","affected_people":12}
```

`category` may be omitted or null; `affected_people` (also accepted as `affectedPeople`) defaults to 1 and must be positive. Title, description, district and valid coordinates are required. To attach files, use multipart form data with an `application/json` part named `challenge` containing the same JSON, and optional `photo` (`image/*`) and `audio` (`audio/*`) parts. Uploads use generated filenames under `UPLOAD_DIR` (default `./uploads`, relative to the application working directory); paths are stored privately in the database. Rolled-back transactions remove uploaded files. Spring's default multipart limits apply (1 MB per file, 10 MB per request). Local disk must persist alongside the database if uploads are to survive deployment/restarts.

- Public `GET /api/challenges?district=Example&category=WATER_SANITATION&status=SUBMITTED&page=0&size=20` combines optional filters. District matches exactly, ignoring case. Page size is 1–100. `GET /api/challenges/{id}` returns the same sanitized fields. Responses omit user contact details, disk paths and internal moderation/AI data.
- `POST /api/challenges/{id}/transitions` takes `{"toState":"VALIDATED","reason":"Reviewed"}`. The table in PLAN.md determines roles and guards; invalid edges return 409 with both states, and wrong roles return 403. Categorization accepts `category`; impact evaluation requires `impactSummary`. Rejection, pilot approval and deployment require a nonblank reason.
- Admin `POST /api/admin/challenges/{id}/assignment` takes `{"universityId":"<UUID>"}`. It assigns only a university and sets a response deadline using `UNIVERSITY_RESPONSE_DEADLINE` (default `P7D`). University `POST /api/challenges/{id}/assignment-response` takes `{"response":"ACCEPTED"}` or `{"response":"DECLINED","reason":"No capacity"}`. Decline clears the assignment and returns to CATEGORIZED. Acceptance leaves the lifecycle at ASSIGNED; pending overdue assignments await admin action.
- The generic transition endpoint cannot create assignments, projects or pledges. The workflow service also requires an accepted assignment and persisted proposal, a valid persisted pledge, completed prototype milestone evidence, or an impact summary for the applicable later states. Project and pledge creation APIs remain for milestone 7.
- Submitter-only `POST /api/challenges/{id}/confirm-fixed` records idempotent citizen feedback at DEPLOYED or IMPACT_EVALUATED. It stores `fixedConfirmedAt` and an audit event without changing lifecycle state.

Submission and every transition persist an event and notifications atomically. Notifications go to the submitter, active admins and relevant university accounts (including the former university on recall/decline). Console SMS/email placeholders run after commit. New submissions await manual moderation; there are no AI calls.

Run workflow checks with `mvn -f core-platform/pom.xml -Dtest=TransitionPolicyTest,WorkflowServiceTest,WorkflowIntegrationTest test`, then the full suite with `mvn -f core-platform/pom.xml test`. Mockito-based tests require JVM attachment permission on this machine.

## AI enrichment and matching (milestone 6)

Start `ai-service` as documented in its README, then start core-platform. `AI_BASE_URL` defaults to `http://localhost:8000`; `AI_TIMEOUT` defaults to `2s` per call (connection included, no retries), and `AI_CONFIDENCE_THRESHOLD` to `0.70`. Submissions call classify and embed, retain suggestions and model-tagged vectors, and stay SUBMITTED until admin validation. Invalid, low-confidence, failed or timed-out AI calls return a usable submission with `aiStatus=MANUAL_REVIEW` and internal moderation notes.

Admin APIs (Bearer JWT required):

- `GET /api/admin/challenges?queue=moderation|allocation|overdue&page=0&size=20` lists review details.
- `PATCH /api/admin/challenges/{id}/classification` with `{"category":"HEALTH"}` confirms the category without bypassing workflow transitions.
- `GET /api/admin/challenges/{id}/duplicates` lists compatible semantic matches within the configured radius. Defaults: `DUPLICATE_SIMILARITY_THRESHOLD=0.85`, `DUPLICATE_RADIUS_KM=10`; both boundaries are inclusive.
- `POST /api/admin/challenges/{id}/merge` with `{"originalId":"uuid"}` links an unallocated duplicate and adds its affected people to the original exactly once. Merged duplicates cannot be allocated or used as originals; manual merges are allowed even when AI has no suggestion.
- `GET /api/admin/challenges/{id}/university-matches` ranks universities by their best faculty score and includes ranked faculty and reasons. Scores combine embedding similarity (50%), category expertise (20%), proximity (15%), capacity (10%) and past acceptance rate (5%). No response history uses a neutral 50% acceptance rate. Only matching models and dimensions are compared. Faculty profile embeddings are requested when ranking; unavailable AI falls back to rules. This minimal implementation scans profiles/challenges in memory.
- Existing `POST /api/admin/challenges/{id}/assignment` accepts any university selected by the admin, including one outside the ranking's top choice.

University accept/decline must arrive strictly before the stored UTC deadline (`UNIVERSITY_RESPONSE_DEADLINE`, default `P7D`). The deadline job runs every 60 seconds (`workflow.deadline-check-interval` in milliseconds), flags unanswered overdue assignments once, and notifies users. State stays ASSIGNED until admin recall and reassignment. Response history survives recall/decline and informs subsequent rankings. V3 adds portable matching flags and assignment history; PostgreSQL acceptance remains pending.
