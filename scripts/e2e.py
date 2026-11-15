#!/usr/bin/env python3
"""Dev-only live HTTP acceptance scenario. Requires a readable core console log.

Adds records; does not reset data. Run without concurrent writes for exact dashboard
deltas. No JWT signing shortcuts, fixed OTPs, database access or HTTP mocks.
"""
import argparse
import json
import re
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--core-url', default='http://localhost:8080')
    parser.add_argument('--ai-url', default='http://localhost:8000')
    parser.add_argument('--core-log', required=True, type=Path)
    parser.add_argument('--ai', choices=['up', 'down'], required=True)
    args = parser.parse_args()
    tokens = {}

    def request(method, path, role=None, body=None, expected=200):
        headers = {'Content-Type': 'application/json'}
        if role:
            headers['Authorization'] = 'Bearer ' + tokens[role]
        req = Request(args.core_url + path, method=method, headers=headers,
                      data=None if body is None else json.dumps(body).encode())
        try:
            with urlopen(req, timeout=45) as response:
                status, data = response.status, response.read()
        except HTTPError as exc:
            raise AssertionError(f'{method} {path}: HTTP {exc.code}: {exc.read().decode()}') from exc
        check(status == expected, f'{method} {path}: expected {expected}, got {status}')
        return json.loads(data) if data else None

    # A stopped service means connection failure, not a responding error endpoint.
    try:
        with urlopen(args.ai_url + '/health', timeout=3) as response:
            health = json.load(response)
        check(args.ai == 'up', 'AI must be stopped for --ai down')
        check(health['status'] == 'ok' and health['mode'] == 'mock', 'Expected mock AI health')
    except URLError as exc:
        check(not isinstance(exc, HTTPError) and args.ai == 'down', f'AI health failed: {exc}')
    check(request('GET', '/actuator/health')['status'] == 'UP', 'Core health')

    for index, role in enumerate(['CITIZEN', 'ADMIN', 'UNIVERSITY', 'INDUSTRY', 'GOVERNMENT']):
        phone = f'900000000{index}'
        offset = args.core_log.stat().st_size
        request('POST', '/api/auth/otp/request', body={'phone': phone})
        deadline = time.monotonic() + 10
        match = None
        while time.monotonic() < deadline:
            with args.core_log.open('rb') as log:
                log.seek(offset)
                match = re.search(rf'Development OTP for {phone}: (\d{{6}})', log.read().decode())
            if match:
                break
            time.sleep(0.1)
        check(match is not None, f'No new dev OTP for {role} in core log')
        tokens[role] = request('POST', '/api/auth/otp/verify', body={'phone': phone, 'otp': match[1]})['accessToken']
        check(request('GET', '/api/users/me', role)['role'] == role, 'Seed role login')
    print('PASS: five seeded roles authenticated through console OTP', flush=True)
    before = request('GET', '/api/government/analytics', 'GOVERNMENT')
    marker = uuid.uuid4().hex[:8]
    report = dict(title=f'Contaminated borewell water in Gumla {marker}',
                  description='Unsafe borewell drinking water in Gumla poses a public health risk. Villagers need water testing and filtration.',
                  latitude=23.044, longitude=84.542, district='Gumla', affected_people=12)
    original = request('POST', '/api/challenges', 'CITIZEN', report, 201)
    duplicate = request('POST', '/api/challenges', 'CITIZEN',
                        dict(report, latitude=23.045, affected_people=3), 201)
    cid, did = original['id'], duplicate['id']
    base = f'/api/challenges/{cid}'
    admin = f'/api/admin/challenges/{cid}'

    def queue():
        page = 0
        result = []
        while True:
            batch = request('GET', f'/api/admin/challenges?queue=moderation&size=100&page={page}', 'ADMIN')
            result.extend(batch['content'])
            if batch['last']:
                return result
            page += 1

    reviews = {r['challenge']['id']: r for r in queue()}
    for item in [original, duplicate]:
        review = reviews[item['id']]
        check(item['status'] == 'SUBMITTED', 'Submission persisted')
        if args.ai == 'up':
            check(review['suggestedCategory'] == 'WATER_SANITATION' and review['confidence'] >= .7, 'AI water classification')
        else:
            check(item['aiStatus'] == 'MANUAL_REVIEW' and review['moderationRequired'] and review['moderationReason'], 'Outage manual moderation')
            check(review['suggestedCategory'] is None, 'No fabricated AI suggestion')
    candidates = request('GET', f'/api/admin/challenges/{did}/duplicates', 'ADMIN')
    if args.ai == 'up':
        check(reviews[did]['duplicateCandidate'], 'Nearby duplicate flag')
        check(any(c['id'] == cid for c in candidates), 'Original in duplicate candidates')
    else:
        check(not candidates, 'No embedding-based candidates during outage')
    merged = request('POST', f'/api/admin/challenges/{did}/merge', 'ADMIN', {'originalId': cid})
    check(merged['duplicateOfId'] == cid, 'Duplicate linked')
    request('POST', f'/api/admin/challenges/{did}/merge', 'ADMIN', {'originalId': cid})
    check(request('GET', base)['affectedPeople'] == 15, 'Idempotent merge count')
    print(f'PASS: submissions, {args.ai} classification path, duplicate merge (15 affected)', flush=True)
    request('PATCH', admin + '/classification', 'ADMIN', {'category': 'WATER_SANITATION'})

    def transition(state, role, **extra):
        result = request('POST', base + '/transitions', role, dict(toState=state, **extra))
        check(result['status'] == state, f'Transition to {state}')

    transition('VALIDATED', 'ADMIN')
    transition('CATEGORIZED', 'ADMIN')
    matches = request('GET', admin + '/university-matches', 'ADMIN')
    # The one seeded university login belongs to University 1; admin may override rank.
    university = next(m for m in matches if m['name'] == 'University 1')
    request('POST', admin + '/assignment', 'ADMIN', {'universityId': university['id']})
    request('POST', base + '/assignment-response', 'UNIVERSITY', {'response': 'ACCEPTED'})
    draft = request('POST', base + '/proposal-draft', 'UNIVERSITY')
    check(draft['draft'] and draft['proposal'].startswith('DRAFT'), 'Draft labeled')
    check(draft['aiStatus'] == ('GENERATED' if args.ai == 'up' else 'TEMPLATE_FALLBACK'), 'Draft AI/fallback path')
    project = request('POST', base + '/project', 'UNIVERSITY',
                      {'title': 'Gumla safe borewell pilot', 'proposalText': draft['proposal'] + '\nReviewed: test, filter and retest the borewell.', 'teamName': 'Gumla Water Team'}, 201)
    path = '/api/projects/' + project['id']
    check(project['status'] == 'PROPOSAL_CREATED', 'Proposal lifecycle')
    team = request('PUT', path + '/team', 'UNIVERSITY', {'name': 'Gumla Water Team', 'members': [
        {'facultyProfileId': f['id'], 'role': 'LEAD' if i == 0 else 'MEMBER'} for i, f in enumerate(university['faculty'][:2])]})
    check(len(team['team']['members']) == 2, 'Persisted faculty team')
    pledge = request('POST', path + '/pledges', 'INDUSTRY',
                     {'supportType': 'FUNDING', 'amount': 50000, 'currency': 'INR', 'description': 'Water testing and pilot filters'}, 201)
    check(request('GET', base)['status'] == 'INDUSTRY_CONNECTED', 'Pledge lifecycle')
    request('POST', path + '/milestones', 'UNIVERSITY',
            {'title': 'Filter prototype', 'description': 'Build and bench test filter', 'dueDate': '2030-01-01', 'status': 'COMPLETED', 'outcome': 'Prototype filter tested; water quality recorded'}, 201)
    transition('PROTOTYPE', 'UNIVERSITY')
    transition('PILOT', 'GOVERNMENT', reason='Approved supervised Gumla water pilot')
    dashboard = request('GET', '/api/government/analytics', 'GOVERNMENT')
    check(dashboard['byStatus']['PILOT'] == before['byStatus']['PILOT'] + 1, 'PILOT dashboard delta')
    check(dashboard['byDistrict']['Gumla'] == before['byDistrict'].get('Gumla', 0) + 2, 'Gumla dashboard delta (includes merged reports)')
    check(dashboard['byCategory']['WATER_SANITATION'] == before['byCategory'].get('WATER_SANITATION', 0) + 1, 'Water category delta')
    check(dashboard['universityParticipation'][university['id']] == before['universityParticipation'].get(university['id'], 0) + 1, 'University dashboard delta')
    check(dashboard['industryPledges'][pledge['industryOrganizationId']] == before['industryPledges'].get(pledge['industryOrganizationId'], 0) + 1, 'Industry dashboard delta')
    check(dashboard['fundingByCurrency']['INR'] == before['fundingByCurrency'].get('INR', 0) + 50000, 'Funding dashboard delta')
    check(request('GET', path, 'GOVERNMENT')['status'] == 'PILOT', 'Government project view')
    for role in tokens:
        notices = request('GET', '/api/notifications', role)
        if role != 'GOVERNMENT':
            check(notices['totalElements'] > 0, f'{role} notifications')
    print('PASS: allocation, acceptance, draft, proposal, faculty team, pledge, prototype, PILOT, dashboard and notifications', flush=True)
    # PLAN.md also asks for final lifecycle verification after the PILOT assertion.
    transition('DEPLOYED', 'GOVERNMENT', reason='Supervised pilot accepted for deployment')
    transition('IMPACT_EVALUATED', 'GOVERNMENT', impactSummary='Pilot water testing and community outcomes recorded')
    check(request('POST', base + '/confirm-fixed', 'CITIZEN')['fixedConfirmedAt'], 'Citizen feedback')
    final = request('GET', '/api/government/analytics', 'GOVERNMENT')
    check(final['resolved'] == before['resolved'] + 1, 'Resolved dashboard delta')
    check(final['byStatus']['IMPACT_EVALUATED'] == before['byStatus']['IMPACT_EVALUATED'] + 1, 'Final dashboard delta')
    print(f'PASS: deployment, impact evaluation and citizen feedback; ai={args.ai}; challenge={cid}; project={project["id"]}', flush=True)


if __name__ == '__main__':
    main()
