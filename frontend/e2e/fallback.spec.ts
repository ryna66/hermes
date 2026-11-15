// Requires the AI service to be STOPPED, while core (dev profile) and Vite stay up.
// Opt in explicitly: E2E_AI_DOWN=1 npm run e2e:fallback
// MANUAL_REVIEW is asserted from the real submission UI; no responses are mocked.
import { expect, test } from '@playwright/test'
import { findReport, login, submitReport, uniqueTitle } from './helpers.ts'

test('AI outage preserves submission and routes it to admin moderation', async ({ page }) => {
  test.skip(process.env.E2E_AI_DOWN !== '1', 'Stop AI and set E2E_AI_DOWN=1 to exercise the real outage fallback')
  const title = uniqueTitle('Gumla borewell AI outage')
  await login(page, 'CITIZEN')
  const challenge = await submitReport(page, title)
  await expect(page.getByRole('status').filter({ hasText: 'AI suggestion status: MANUAL_REVIEW' })).toBeVisible()
  await page.goto('/my-challenges')
  await expect(await findReport(page, title, challenge, 'own')).toContainText('SUBMITTED')

  await login(page, 'ADMIN')
  await page.goto('/admin/validation')
  const report = await findReport(page, title, challenge, 'admin')
  await expect(report).toContainText('Status: SUBMITTED')
  await expect(report.locator('dt').filter({ hasText: /^AI suggestion$/ }).locator('xpath=following-sibling::dd[1]')).toHaveText('None')
  await expect(report.locator('dt').filter({ hasText: /^Moderation reason$/ }).locator('xpath=following-sibling::dd[1]')).not.toHaveText('None')
  await expect(report.getByRole('button', { name: 'Validate', exact: true })).toBeVisible()
})
