import { expect, test } from '@playwright/test'
import { detailValue, findReport, login, move, submitReport, uniqueTitle } from './helpers.ts'

test('Gumla borewell: citizen report through government PILOT', async ({ page }) => {
  const title = uniqueTitle()
  const projectTitle = `Water testing project ${title}`
  const teamName = `Gumla team ${Date.now()}`
  const milestoneTitle = `Filter prototype ${Date.now()}`

  await login(page, 'CITIZEN')
  const original = await submitReport(page, title)
  await page.goto('/my-challenges')
  await expect(await findReport(page, title, original, 'own')).toContainText('SUBMITTED')
  // Same text and affected count, roughly 111 metres north of the first report.
  const duplicate = await submitReport(page, title, '23.045')
  expect(duplicate).not.toBe(original)
  await page.goto('/my-challenges')
  await expect(await findReport(page, title, duplicate, 'own')).toContainText('SUBMITTED')

  await login(page, 'ADMIN')
  await page.goto('/admin/validation')
  let report = await findReport(page, title, duplicate, 'admin')
  await report.getByLabel('Original challenge ID', { exact: true }).fill(original.split('/').at(-1)!)
  await report.getByRole('button', { name: 'Merge duplicate', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: `${title}: merged into` })).toBeVisible()
  await page.goto(original)
  await detailValue(page, 'Affected people', '10')

  await page.goto('/admin/validation')
  report = await findReport(page, title, original, 'admin')
  await report.getByLabel('Category', { exact: true }).selectOption('WATER_SANITATION')
  await report.getByRole('button', { name: 'Confirm category', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: `${title}: category confirmed.` })).toBeVisible()
  // Mutations rebuild the queue and may change pagination; rediscover by run title/ID.
  await page.goto('/admin/validation')
  report = await findReport(page, title, original, 'admin')
  await report.getByRole('button', { name: 'Validate', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: `${title}: validated.` })).toBeVisible()
  await page.goto('/admin/validation')
  report = await findReport(page, title, original, 'admin')
  await report.getByLabel('Category', { exact: true }).selectOption('WATER_SANITATION')
  await report.getByRole('button', { name: 'Categorize', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: `${title}: categorized.` })).toBeVisible()

  await page.goto('/admin/allocation')
  report = await findReport(page, title, original, 'admin')
  await report.getByRole('button', { name: 'View ranked universities', exact: true }).click()
  // Explicitly select University 1 regardless of ranking position.
  await report.getByRole('button', { name: 'Select University 1', exact: true }).click()
  const universityId = await report.getByLabel('University ID', { exact: true }).inputValue()
  expect(universityId).toMatch(/^[0-9a-f-]{36}$/i)
  await report.getByRole('button', { name: 'Assign university', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: `${title}: university assigned.` })).toBeVisible()

  await login(page, 'UNIVERSITY')
  await page.goto('/university/inbox')
  report = await findReport(page, title, original, 'inbox')
  await report.getByRole('button', { name: 'Accept assignment', exact: true }).click()
  await expect(report).toHaveCount(0)
  // PENDING filter removes the accepted report. Reopen under ACCEPTED.
  await page.getByLabel('Assignment response', { exact: true }).selectOption('ACCEPTED')
  report = await findReport(page, title, original, 'inbox')
  await report.getByRole('button', { name: 'Generate draft', exact: true }).click()
  await expect(report.getByRole('status').filter({ hasText: 'DRAFT — the team must review' })).toBeVisible()
  const draft = await report.getByLabel('Proposal text', { exact: true }).inputValue()
  expect(draft.trim().length).toBeGreaterThan(0)
  await report.getByLabel('Project title', { exact: true }).fill(projectTitle)
  await report.getByLabel('Team name', { exact: true }).fill(teamName)
  await report.getByLabel('Proposal text', { exact: true }).fill(`${draft}\nTeam reviewed: survey, test water and build a filter prototype.`)
  // The API persists the reviewed proposal and creates the project together.
  await report.getByRole('button', { name: 'Create project', exact: true }).click()
  await expect(page).toHaveURL(/\/projects\/[0-9a-f-]+$/i)
  await expect(page.getByRole('heading', { name: projectTitle, exact: true })).toBeVisible()
  const projectPath = new URL(page.url()).pathname
  await page.getByRole('button', { name: 'Save proposal', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Proposal saved.' })).toBeVisible()

  await page.getByRole('link', { name: 'University departments', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/universities/${universityId}/departments$`))
  const faculty = page.getByRole('main').getByRole('article')
  await expect(faculty.first()).toBeVisible()
  const facultyIds = (await faculty.locator('p').filter({ hasText: /^Faculty ID: / }).allTextContents()).map(text => text.replace('Faculty ID: ', '').trim())
  expect(facultyIds.length).toBeGreaterThanOrEqual(2)
  await page.goto(projectPath)
  await page.getByRole('button', { name: 'Add team member', exact: true }).click()
  await page.getByRole('button', { name: 'Add team member', exact: true }).click()
  await page.getByLabel('Faculty profile ID', { exact: true }).nth(0).fill(facultyIds[0])
  await page.getByLabel('Team role', { exact: true }).nth(0).selectOption('LEAD')
  await page.getByLabel('Faculty profile ID', { exact: true }).nth(1).fill(facultyIds[1])
  await page.getByLabel('Team role', { exact: true }).nth(1).selectOption('MEMBER')
  await page.getByRole('button', { name: 'Save team', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Team saved.' })).toBeVisible()

  const add = page.locator('form').filter({ has: page.getByRole('heading', { name: 'Add milestone', exact: true }) })
  await add.getByLabel('Milestone title', { exact: true }).fill(milestoneTitle)
  await add.getByLabel('Description', { exact: true }).fill('Build and bench test a borewell filter prototype')
  await add.getByLabel('Due date', { exact: true }).fill('2030-01-01')
  await add.getByRole('button', { name: 'Add milestone', exact: true }).click()
  await expect(add.getByRole('status').filter({ hasText: 'Milestone saved.' })).toBeVisible()
  const milestone = page.getByRole('article').filter({ has: page.getByRole('heading', { name: milestoneTitle, exact: true }) })
  await expect(milestone).toBeVisible()
  await milestone.getByLabel('Progress', { exact: true }).selectOption('COMPLETED')
  await milestone.getByLabel('Outcome', { exact: true }).fill('Prototype filter tested; water quality recorded for the pilot.')
  await milestone.getByRole('button', { name: 'Update milestone', exact: true }).click()
  await expect(milestone.getByText('Outcome: Prototype filter tested; water quality recorded for the pilot.', { exact: true })).toBeVisible()

  await login(page, 'INDUSTRY')
  await page.goto(projectPath)
  await page.getByLabel('Support type', { exact: true }).selectOption('FUNDING')
  await page.getByLabel('Amount', { exact: true }).fill('50000')
  await page.getByLabel('Currency', { exact: true }).fill('INR')
  await page.getByLabel('Support description', { exact: true }).fill(`Fund borewell pilot for ${title}`)
  await page.getByRole('button', { name: 'Submit pledge', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Pledge recorded.' })).toBeVisible()
  await expect(page.getByText('Status: INDUSTRY_CONNECTED', { exact: true })).toBeVisible()

  // Required state-machine edge before government approval; evidence exists above.
  await login(page, 'UNIVERSITY')
  await page.goto(projectPath)
  await move(page, 'PROTOTYPE')
  await expect(page.getByText('Status: PROTOTYPE', { exact: true })).toBeVisible()

  await login(page, 'GOVERNMENT')
  await page.goto(projectPath)
  await move(page, 'PILOT', 'Reviewed completed prototype evidence; approve a supervised Gumla pilot.')
  await expect(page.getByText('Status: PILOT', { exact: true })).toBeVisible()

  await page.goto(original)
  await detailValue(page, 'Status', 'PILOT')
  const timeline = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Lifecycle timeline', exact: true }) }).getByRole('listitem')
  // Acceptance changes the response only; it does not add a lifecycle event.
  const expected = [
    'New report → SUBMITTED', 'SUBMITTED → VALIDATED', 'VALIDATED → CATEGORIZED',
    'CATEGORIZED → ASSIGNED', 'ASSIGNED → PROPOSAL_CREATED',
    'PROPOSAL_CREATED → INDUSTRY_CONNECTED', 'INDUSTRY_CONNECTED → PROTOTYPE', 'PROTOTYPE → PILOT',
  ]
  await expect(timeline).toHaveCount(expected.length)
  for (const [index, transition] of expected.entries()) await expect(timeline.nth(index)).toContainText(transition)
  const dates: number[] = []
  for (const time of await timeline.locator('time').all()) dates.push(Date.parse(await time.getAttribute('datetime') || ''))
  expect(dates.every(Number.isFinite)).toBe(true)
  expect(dates).toEqual([...dates].sort((a, b) => a - b))
})
