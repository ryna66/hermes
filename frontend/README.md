# Frontend foundation (milestone 13)

React, TypeScript and Vite; semantic HTML without styling. From this directory:

```sh
npm run dev
npm run build
npm test
```

Dependencies are already installed. Copy `.env.example` to `.env.local` to override
`VITE_API_URL` (default `http://localhost:8080`, without `/api`). Restart Vite after
changing it. Spring dev CORS permits localhost:5173; configure
`CORS_ALLOWED_ORIGINS` if using another origin. The Vite development server handles
page-route fallback; production static hosting must also serve index.html for page URLs.

Login requests an OTP, then verifies it. Citizen registration also requests an OTP.
In dev, read the OTP in the core service console. Tokens and their backend expiry
are kept in sessionStorage; reload fetches `/api/users/me` to discover the role.
Expiry and HTTP 401 clear authentication. No role is trusted from browser storage.

All PLAN.md section 7 routes exist. Login/register, landing, explorer and challenge
detail, citizen submission, my challenges, notifications, admin, university, industry and government pages are implemented. Navigation shows public and permitted
role pages. Dynamic challenge, university and project URLs require real IDs; no
fictional links are supplied. Project guards allow the four controller reader roles;
the backend still enforces university ownership and industry visibility.
Notifications poll immediately and every 60 seconds while authenticated, traversing
all pages to count unread items; the API has no dedicated unread-count endpoint.

## Contract audit

`src/api.ts` and `src/types.ts` follow AuthController, CurrentUserController,
PlannedApiController/Service, ChallengeController/Service/Input,
AdminMatchingController, MatchingService, DuplicateService,
CollaborationController/Service, NotificationController and AnalyticsController/Service.
Errors follow ApiErrors and ApiExceptionHandler, including the optional
`fieldErrors: [{field, message}]` array. Non-JSON HTTP failures get a status-based
message. Network failures remain Error objects handled by the shared display.

All section 7 API endpoints were found, including transcription, own challenges,
departments/faculty replacement and inbox. No missing endpoint was invented.
The public page `/universities/:id/departments` uses API
`/api/organizations/:id/departments`. Challenge detail fields are flat, plus timeline
and allowedTransitions. API wrappers also include provisioning, confirm-fixed and
the industry matched-problems alias. FacultyInput includes email but the returned
Faculty DTO does not. The current-account DTO has no organizationId; department editing requires the
university user to identify their own organization (see milestone 17 below).

Vitest covers backend errors, bearer headers, multipart requests, 401/403 behavior,
storage restoration, automatic expiry and all 25 role/guard combinations, plus
anonymous and loading cases. Live browser/OTP integration is deferred to milestone 19.

## Public pages (milestone 14)

The landing page requests the five most recent public reports (backend sorts by
createdAt descending). Explorer filters and page are stored in the URL; applying
filters resets pagination. Its map shows only the current result page with safe
text popups and fits those coordinates. OpenStreetMap tile failures show a fallback
message while the challenge list and coordinates remain usable. Only Leaflet's
required CSS and a map height are used; no application styling is added.

Challenge detail displays public DTO fields and a chronological lifecycle timeline.
Buttons come exclusively from allowedTransitions. Assignment, project creation and
pledging use their dedicated resource APIs; other actions use transitions with
reason/category/impactSummary as appropriate. University acceptance and persisted
prototype evidence remain backend prerequisites. Forms display backend errors,
including 403 and 409. The pledge form asks for the project ID and verifies its
challengeId before submitting: the public challenge DTO has no projectId.

Citizen ownership is checked through all pages of /users/me/challenges because the
public response deliberately has no submitter ID. Confirm fixed appears only for a
verified owner at DEPLOYED or IMPACT_EVALUATED who has not already confirmed.

Milestone checks: npm run build and npm test pass (50 tests). Map tile failure is
simulated in unit tests; live browser/tile-server and OTP journeys were not run.

## Citizen pages (milestone 15)

`/submit` accepts problem details, a Leaflet click location, browser geolocation or
manual coordinates, and optional photo/audio attachments. The existing API wrapper
sends the JSON `challenge` part with `photo` and `audio` file parts when attachments
are present. Validation mirrors ChallengeInput and attachment media types, including
Spring's default 1 MiB per-file upload limit. Success shows aiStatus and a detail link.

Voice notes can be uploaded or recorded with MediaRecorder. Recording and geolocation
require browser permission and a secure context (HTTPS or localhost); unsupported or
denied access shows a message and leaves manual entry available. Stopping recording
or leaving the page releases microphone tracks. Transcription sends base64 audio,
its MIME type and language `en`, enforcing the endpoint's 1 MiB limit. Returned text
fills the editable description; MANUAL_ENTRY displays the backend message and
preserves typed text. Selected audio is also attached on submission; it can be removed.

`/my-challenges` paginates the citizen's own reports, showing status, rejection reason
when REJECTED, and detail links. `/notifications` is available to all authenticated
roles, paginates their notifications and marks unread items read. Successful reads
refresh the shared unread badge. No backend changes or dependencies were needed.

Milestone checks: npm run build and the full frontend npm test suite pass (63 tests).
Browser microphone, geolocation, live map tiles and live API integration remain
unverified; unit tests simulate recording, geolocation, map selection and tile failure.

## Admin pages (milestone 16)

`/admin/validation` and `/admin/allocation` provide paginated moderation, allocation
and overdue queue tabs, with the selected queue in the URL. Each report shows its
AI suggestion, confidence and moderation reason. Unallocated reports support category
confirmation/editing, duplicate candidates with similarity/distance, explicit merging
into a selected original, validation, categorization and rejection with a nonblank
reason. Controls follow the backend state table; server errors remain authoritative.

Categorized reports can load ranked universities with scores and reasons, select any
ranked result or enter another university UUID, then assign. Overdue assignments can
be recalled into allocation. Mutations show success or backend errors and refresh the
queue, returning to a previous page if the current page becomes empty.

`/admin/accounts` provisions UNIVERSITY, INDUSTRY and GOVERNMENT accounts with name,
phone and a matching organization UUID. The API has no organization directory or
account-list endpoint, so organization IDs must be obtained separately; successful
provisioning displays the returned account and clears the form. No backend endpoints,
dependencies or styling were added.

Milestone checks: npm run build and full frontend npm test passed (77 tests, including
14 admin tests). Tests cover queue switching/pagination, merge direction and refresh,
nonblank rejection reasons, classification and lifecycle actions, ranking reasons,
lower-ranked/manual university choices, overdue recall, backend errors and all three
provisioned roles. Checks ran inside the sandbox; live browser/API verification remains
pending for milestone 19.


## University pages (milestone 17)

`/university/inbox` provides paginated assignments with All, PENDING, ACCEPTED and
DECLINED filters, response deadlines and an overdue marker. Pending assignments
can be accepted or declined with a nonblank reason; overdue response controls are
disabled. Accepted assignments without a project expose the proposal editor,
generate-draft button and project creation. Generated and template-fallback results
always carry a DRAFT notice requiring team review, regardless of the API draft flag.
Draft failures preserve typed text; successful creation opens the project workspace.

`/universities/:id/departments` publicly groups faculty by department and displays
IDs for team selection. UNIVERSITY users can replace a department's faculty, add
profiles and remove unreferenced profiles. Updates preserve IDs and omit private
email fields so the backend retains existing email values. HTTP 409 referenced
faculty errors display the backend message and retain the edited list.

Account context gap: `/users/me` has no organization ID and there is no own-department
GET endpoint. The inbox accepts an organization UUID for viewing departments; a
university project also links its owning organization. Editing requires explicit
confirmation that the displayed organization is the user's university and that the
list is complete. PUT always targets the authenticated university via `/organizations/me`,
and the backend checks supplied faculty IDs against its organization and department.
The frontend cannot independently establish ownership for an arbitrary public
organization URL; no backend or directory endpoint was added.

`/projects/:id` shows project/proposal/team details and milestone progress/outcomes.
University readers can edit the proposal and impact summary, replace team members
using faculty IDs and LEAD/MEMBER roles, and add/update milestones. COMPLETED
milestones require an outcome. Other reader roles see details without university
editors. Lifecycle buttons use the challenge's allowedTransitions and the existing
shared action form; successful transitions refresh the project and allowlist.

Draft API constraint: `/challenges/:id/proposal-draft` requires an ASSIGNED challenge
with an ACCEPTED response. Creating a project moves it to PROPOSAL_CREATED, so the
existing project's generate button is disabled with an explanation. Generate and
review the draft in the inbox before project creation; regeneration for an existing
project would require a backend change.

Milestone checks: `npm run build` and full frontend `npm test` passed (95 tests,
including 18 university tests for responses/filtering, generated/fallback draft
labelling, creation, faculty replacement/409 errors, proposal/team/milestone edits,
allowed transitions and reader-role controls). Both checks ran inside the sandbox.
Live browser and API verification remains pending for milestone 19.


## Industry and government pages (milestone 18)

`/industry` paginates support-facing project discovery and the signed-in industry's
own organization pledges independently. Discovery divides the current result page
into projects awaiting their first pledge (PROPOSAL_CREATED) and projects already
supported by industry (INDUSTRY_CONNECTED and later states); both groups can receive
support. The own-pledges table links to projects supported by this organization.
Project links open `/projects/:id`, showing the industry-facing ID, university,
status, impact summary and milestone progress/outcomes. Proposal/team details and
university editing controls are hidden for industry readers, matching the API view.

The project pledge form offers FUNDING, IN_KIND and MENTORSHIP. Funding requires a
positive amount with at most two decimal places and a three-letter uppercase
currency; every type requires a nonblank description. Non-funding payloads omit
amount/currency even after switching from funding. Successful pledges refresh the
project and link back to the own-pledges list. Backend errors retain entered fields;
a refresh error after a successful pledge explicitly reports that it was recorded.

`/government` uses `/api/government/analytics` for status/category/district counts,
university assignment counts, industry/type pledge counts, funding per currency,
open/resolved/rejected totals, unique incidents and completed lifecycle-stage
average seconds/sample counts. Recharts bar charts show status and category counts,
including empty data; matching HTML tables retain accessible values. District rows
sort by descending report count with alphabetical tie-breaking. Charts use their
required dimensions and default rendering; no application CSS was added.

Analytics gaps: `uniqueIncidents` is a global count excluding merged duplicates;
existing status/category/district counts include all reports. There is no district
unique-incident breakdown, so the page labels the global count and does not invent
per-district values. Analytics includes no project IDs, and no government project
list or challenge-to-project lookup exists. Status/category/district counts link to
filtered public reports (UNCATEGORIZED has no supported explorer filter). A known
project-ID lookup uses the authorized project endpoint and returns a project-page
link. University counts link to public departments. There are no organization-name
lookups for industry counts; IDs are displayed as returned. No backend changes.

Government project readers can run PILOT, DEPLOYED and IMPACT_EVALUATED only when
returned by the challenge's allowedTransitions. The shared action form submits the
required reason or impact summary, displays backend errors and refreshes project
status and available actions. The API remains authoritative for lifecycle guards.

Milestone checks: `npm run build` passed and full frontend `npm test` passed
(119 tests, including 24 industry/government tests). Both ran inside the sandbox.
Vite reports a bundle-size warning after including Recharts; no build failure.
Live browser/API checks remain pending for milestone 19. No dependencies installed,
application styling or backend changes; no commit created.

## Live browser tests (milestone 19b)

Playwright uses installed Chromium, one worker and real UI/API calls. Start the
mock AI and dev core yourself using the root README commands (core output must
be written to `/tmp/sih-core.log`), then start Vite in a separate terminal:

```sh
cd frontend
npm run dev -- --host localhost --port 5173 --strictPort
```

From another terminal, at the repository root:

```sh
cd frontend
SIH_CORE_LOG=/tmp/sih-core.log npm run e2e
# Optional frontend origin override:
E2E_BASE_URL=http://localhost:5173 SIH_CORE_LOG=/tmp/sih-core.log npm run e2e
```

The suite does not start or stop services. `E2E_BASE_URL` defaults to
`http://localhost:5173`; the frontend's `VITE_API_URL` must point to your running
core (default `http://localhost:8080`), with its origin allowed by core CORS.
Use the dev seeds: citizen `9000000000`, admin `9000000001`, university
`9000000002` (University 1), industry `9000000003`, government `9000000004`.
The login helper requests OTP through the UI and polls only log bytes appended
after that request, taking the newest matching six-digit code. `SIH_CORE_LOG`
defaults to `/tmp/sih-core.log` and must be readable from the test process.
Do not rotate/truncate the log or log in to these accounts concurrently with tests.
No tokens are printed or saved to a reusable authentication file.

`gumla.spec.ts` submits two reports with the same unique timestamp-suffixed title
and identical description, at nearby coordinates entered through the UI. It
finds that run's title across paginated lists and uses the submission links to
distinguish the duplicate from the original. It merges into the original,
checks 10 affected people, selects University 1 regardless of rank, reviews and
saves a DRAFT proposal, builds a two-faculty team using IDs from the department
page, completes a prototype milestone, pledges INR 50,000, moves to PROTOTYPE
(the prerequisite for PILOT), approves PILOT with a reason, and checks all eight
lifecycle events in order. Acceptance changes the assignment response without
adding a lifecycle event. `permissions.spec.ts` checks wrong-role/anonymous
redirects and the actual backend error message for a deliberately wrong OTP.
Earlier records are retained; tests use unique titles and UI IDs rather than
assuming empty queues or fixed organization/project IDs.

To test fallback, stop **only AI** with Ctrl-C in its terminal and leave core and
Vite running. From the repository root, run:

```sh
cd frontend
E2E_AI_DOWN=1 SIH_CORE_LOG=/tmp/sih-core.log npm run e2e:fallback
```

The fallback test skips unless `E2E_AI_DOWN=1`; it requires a real outage and
asserts MANUAL_REVIEW on successful citizen submission and the same report in
admin moderation with no fabricated AI suggestion. Restart AI using the root
README command afterward. Normal tests and fallback are separate npm scripts.
Failures retain a trace and screenshot under `frontend/test-results/`; successful
tests retain neither. The tests have no `webServer` configuration, API mocks,
database writes outside the UI, or fixed OTPs. No browser tests were run in the
sandbox; live acceptance remains pending your execution of these commands.

Offline checks remain separate:

```sh
cd frontend
npm run lint
npm run build
npx vitest run
```
