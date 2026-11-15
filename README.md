# SIH26043

Phase 1 backend: Java 21 / Maven 3.9+, Spring Boot 3.3.5 and Python 3.11+ / FastAPI. Includes OTP/JWT, challenge workflow, AI fallback, duplicate review, university matching, projects, pledges, government analytics and 13 bare HTML pages. See [verification and production readiness](#verification-and-production-readiness) for acceptance results and production gaps.

Run from the repository root. Install Python dependencies once:

```sh
python3 -m venv ai-service/.venv
ai-service/.venv/bin/python -m pip install -e './ai-service[test]'
```

Terminal 1 (AI service, default mock mode, no provider credentials):

```sh
cd ai-service
AI_MODE=mock .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 2>&1 | tee /tmp/sih-ai.log
```

Terminal 2 (core service, dev/H2):

```sh
# macOS; on other systems set JAVA_HOME to your Java 21 installation
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -f core-platform/pom.xml clean verify
AI_BASE_URL=http://localhost:8000 SPRING_PROFILES_ACTIVE=dev mvn -f core-platform/pom.xml spring-boot:run 2>&1 | tee /tmp/sih-core.log
```

Terminal 3 (live acceptance; reads fresh OTPs from the dev log and adds records):

```sh
python3 scripts/e2e.py --core-log /tmp/sih-core.log --ai up
# Stop only the AI service with Ctrl-C in terminal 1; leave core running.
python3 scripts/e2e.py --core-log /tmp/sih-core.log --ai down
# Restart AI with terminal 1's command when finished.
```

Both runs use actual HTTP and all five seeded OTP logins, merge two nearby reports,
create a proposal and faculty team, pledge INR 50,000, verify PILOT dashboard deltas,
then verify deployment, impact evaluation and citizen feedback. During an outage,
classification and duplicate review are manual and the draft uses a template.
Run against dev data without concurrent writes; each run leaves its records behind.
`--core-url` and `--ai-url` override the defaults; the core AI_BASE_URL must match.
The log contains dev OTPs: keep it local and delete it when finished.

Full test suites:

```sh
mvn -f core-platform/pom.xml clean verify
(cd ai-service && .venv/bin/python -m pytest -q)
```

Use a Java 21 `JAVA_HOME` (macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Default profile is `dev`: Flyway creates all tables, Hibernate validates mappings, then the dev-only seeder logs 10 universities, 30 faculty, 20 challenges, 5 industry organizations, one government organization and five users (one per role). Submission events and notifications accompany seeded challenges. H2 data disappears when the JVM stops.

The Maven build automatically selects an installed Java 21 JDK for compilation and tests, even if `mvn --version` reports another Java version. A Java 21 JDK must be installed; for a nonstandard installation, set `JAVA21_HOME` to its directory or register it in `~/.m2/toolchains.xml`. This uses Maven's [JDK toolchain discovery](https://maven.apache.org/plugins/maven-toolchains-plugin/toolchains/jdk-discovery.html). Without a matching JDK, the build stops during validation. Setting only the Java bytecode target does not select the test JVM: running tests on Java 25 previously caused Mockito's Byte Buddy instrumentation to fail with `Java 25 (69) is not supported`. Keep `JAVA_HOME` on Java 21 when using `spring-boot:run` directly.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Repositories use JPA; schema changes belong in new Flyway migrations. Lifecycle and assignment ownership rules are enforced by the workflow service. Project/team and pledge APIs are implemented.

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
   export DB_URL='jdbc:postgresql://localhost:5433/sih'
   export DB_USERNAME=sih
   export JWT_SECRET='replace-with-a-random-secret-of-at-least-32-bytes'
   # Keep DB_PASSWORD from step 2 in this shell.
   mvn -f core-platform/pom.xml spring-boot:run
   ```

   Flyway applies the same V1–V3 SQL automatically; Hibernate validates it. The prod profile never seeds sample data. Check startup for migration success and open `/actuator/health` and Swagger UI. Prod requires all three DB variables and JWT_SECRET, with no credential defaults. OTP delivery is not configured in prod; auth OTP requests return 503 until a real delivery implementation is supplied.

4. Restart the app with the same environment to retain data. `docker compose --profile database down` stops PostgreSQL while preserving its named volume. Do not remove the volume if you need the data.
5. To return to temporary H2, stop the app, run `unset SPRING_PROFILES_ACTIVE DB_URL DB_USERNAME DB_PASSWORD`, then run the default startup command.

The SQL uses UUID, standard text/numeric/boolean/date/timestamp types and foreign keys; no vendor-specific extensions. PostgreSQL execution must be verified with a running daemon; a Docker client alone is insufficient.

## Authentication

API clients use `Authorization: Bearer <accessToken>`; no session or cookie authentication is enabled yet.

- `POST /api/auth/register` with `{"name":"Example","phone":"9876543210"}` creates a CITIZEN and starts OTP verification. Client-supplied roles never grant privileges. Duplicate phones return 409.
- `POST /api/auth/otp/request` with `{"phone":"9000000000"}` starts login for an existing active user. Read the random six-digit OTP from the dev console.
- `POST /api/auth/otp/verify` with `{"phone":"9000000000","otp":"<six digits>"}` returns `accessToken`, `tokenType` (`Bearer`) and `expiresAt`.
- `GET /api/users/me` returns the authenticated user's ID, name and role.

Seeded login credentials (dev only; no passwords or fixed OTP):

| Role | Phone | Organization |
| --- | --- | --- |
| CITIZEN | `9000000000` | — |
| ADMIN | `9000000001` | — |
| UNIVERSITY | `9000000002` | University 1 |
| INDUSTRY | `9000000003` | Industry 1 |
| GOVERNMENT | `9000000004` | Government |

Each uses the OTP request/verify flow above; read a fresh six-digit code from the core console. Swagger's **Authorize** accepts the returned access token.

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
- The generic transition endpoint cannot create assignments, projects or pledges. The workflow service also requires an accepted assignment and persisted proposal, a valid persisted pledge, completed prototype milestone evidence, or an impact summary for the applicable later states. Use the dedicated project and pledge APIs to create those resources.
- Submitter-only `POST /api/challenges/{id}/confirm-fixed` records idempotent citizen feedback at DEPLOYED or IMPACT_EVALUATED. It stores `fixedConfirmedAt` and an audit event without changing lifecycle state.

Submission and every transition persist an event and notifications atomically. Notifications go to the submitter, active admins and relevant university accounts (including the former university on recall/decline). Console SMS/email placeholders run after commit. Submissions use best-effort AI classification and embedding; admin validation is always required.

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

University accept/decline must arrive strictly before the stored UTC deadline (`UNIVERSITY_RESPONSE_DEADLINE`, default `P7D`). The deadline job runs every 60 seconds (`workflow.deadline-check-interval` in milliseconds), flags unanswered overdue assignments once, and notifies users. State stays ASSIGNED until admin recall and reassignment. Response history survives recall/decline and informs subsequent rankings. V3 adds portable matching flags and assignment history. V1–V3 migrations and schema validation were verified on H2 and PostgreSQL 16; see the verification section below.


## Verification and production readiness

Verified 2026-11-15 on Java 21.0.9, Maven 3.9.11, Python 3.12.0, mock-mode FastAPI/Uvicorn and PostgreSQL 16. Python 3.11 was not installed locally and was not separately tested.

## Final results

| Check | Result | Evidence |
| --- | --- | --- |
| Full core suite: `mvn -f core-platform/pom.xml clean verify` with Java 21 | PASS | 692 tests; 0 failures, errors or skips; executable jar packaged |
| Full AI suite: `cd ai-service && python3 -m pytest -q` | PASS | 7 tests |
| Live story, core + mock AI running | PASS | `python3 scripts/e2e.py --core-log /tmp/sih-m9-core.log --ai up`; challenge `d66a74fd-f646-4088-ad66-a3bd5834a1ee` |
| Same live story, AI process stopped, core still running | PASS | `python3 scripts/e2e.py --core-log /tmp/sih-m9-core.log --ai down`; challenge `f35f2ede-e5ba-45d2-8a3a-2a3f64ff63de` |
| H2 live startup and health | PASS | Dev seeder, V1–V3 migrations, Hibernate validation; `/actuator/health` UP |
| Live Swagger UI and OpenAPI | PASS | `/swagger-ui/index.html` and `/v3/api-docs` HTTP 200 |
| PostgreSQL 16 fresh migration and prod-profile startup | PASS | Isolated `sih_m9` database on port 5433 applied V1–V3; all Flyway success flags true; Hibernate validation passed; health UP on 8081 |
| Bare HTML page routing and role gates | PASS | 8 PageController tests included in core suite; no browser form journey claimed |

First live attempts failed classification/proposal checks. Fixes: force Java AI HTTP/1.1 (Uvicorn rejected h2c upgrade request bodies); populate proposal expertise from faculty; eagerly fetch actor organization for access after the lookup transaction ends. Regression assertions cover each issue. Both full suites and both live scenarios passed after fixes. A script assertion expecting government notifications was corrected: planned recipients are citizens, admins, relevant university users and supporting industry users. All roles can read their own notification endpoint.

## Repeatable live scenario

Use the exact setup, service, OTP and test commands in [README.md](README.md). The standard-library script uses real HTTP, freshly printed dev OTPs and issued bearer JWTs; it does not sign tokens, access the database or replace endpoints with stubs. It needs a readable core log and dev seeds. Run without concurrent writes. Runs add records and compare dashboard deltas, so they can repeat without restarting core.

A citizen reports contaminated borewell drinking water in Gumla. The single-category contract returns `WATER_SANITATION` for this water/public-health concern; there is no combined Water/Public Health enum. Two reports use identical text at coordinates about 111 metres apart. Hash-based mock embeddings are identical, testing real duplicate flagging and admin merge deterministically, without establishing semantic model quality. Merge and repeat merge leave 15 affected people on the original.

Admin confirms category, validates, categorizes and allocates to University 1 (an allowed ranking override). Its user accepts, requests a labeled draft, saves a reviewed proposal and creates a two-faculty team. Industry 1 pledges INR 50,000. Completed prototype evidence permits PROTOTYPE; government approves PILOT. The script checks PILOT, district, category, university, industry and funding deltas and government project access, then verifies DEPLOYED, IMPACT_EVALUATED, citizen feedback and resolved counts. District analytics currently count original and merged reports; these are report counts, not deduplicated incident counts.

For the second run the AI process is actually stopped; a connection-failure check confirms this. Submissions succeed and remain SUBMITTED with MANUAL_REVIEW, moderation reasons and no fabricated category suggestion. There are no semantic duplicate candidates without embeddings. Admin classifies and merges manually; ranking uses rules, drafting returns TEMPLATE_FALLBACK, and the workflow completes. Existing core data remains available throughout the outage.

PostgreSQL acceptance used a separate database/role and prod profile without seeds. It covers migrations, mapping validation and live health. The complete business story ran on dev H2, not PostgreSQL. Temporary verification processes/database/role were removed afterward; the pre-existing PostgreSQL container and application database were preserved.

## What is mocked and what is real

| Mocked / placeholder | Real implementation verified |
| --- | --- |
| Dev console OTP delivery; pending OTPs in memory | Expiry, single use, attempt limits, signed JWTs and role/organization authorization |
| Keyword classification, hash embeddings, transcription stub, proposal templates | Java-to-FastAPI HTTP contracts, confidence validation, timeouts and outage fallback |
| Synthetic universities, faculty, industry organizations and citizens | Spring Data JPA persistence, Flyway migrations, workflow guards, proposal/team/milestone and pledge records |
| Console SMS/email placeholders; pledges are promises, not payments | Persisted events, private in-app notifications, matching rules, Haversine/cosine calculations and analytics |
| Scenario prototype results and deployment/impact summaries | Required evidence guards and government authorization; no independent measurement of water safety |

## Production work

- Implement and evaluate actual classification, semantic embedding, transcription and drafting providers. AI_MODE=real currently returns not-configured responses. Select models, credentials, retention policy, language support and confidence thresholds; benchmark accuracy and duplicate quality on local reports.
- Implement production OTP delivery and shared expiring OTP storage; add abuse/rate controls. Use managed JWT secrets, rotation, TLS and account provisioning/recovery. Keep console OTPs and seed identities out of production.
- Use managed PostgreSQL credentials, backups, restore checks and monitoring. H2 resets on restart. Run the full business acceptance scenario on a provisioned PostgreSQL staging environment, including restart, concurrency and rollback checks.
- Replace local uploads with durable private storage, content validation, upload limits and retention. Add real SMS/email delivery with reliable dispatch and retry handling.
- Replace full-record matching/discovery/analytics scans and per-ranking faculty embedding calls as volume grows; cache embeddings and evaluate PostGIS/pgvector behind existing interfaces. Define whether analytics should exclude merged duplicates.
- Complete planned API gaps: own citizen challenges, transcription intake, organization/department faculty details and updates, university inbox and privileged-user provisioning. Bare pages expose API links and gaps; browser forms, secure cookie auth and CSRF-protected form flows remain absent. Phase 1 has no CSS or frontend framework.
- Add operational alerting, audit access/retention, privacy controls, load testing and provider failure monitoring. Pledges do not transfer funds; deployment/impact entries need a real-world verification process before outcome claims.
