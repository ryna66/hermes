import { expect, test } from '@playwright/test'
import { login, phones, requestOtp } from './helpers.ts'

test('citizen visiting an admin route reaches not allowed', async ({ page }) => {
  await login(page, 'CITIZEN')
  await page.goto('/admin/validation')
  await expect(page).toHaveURL(/\/not-allowed$/)
  await expect(page.getByRole('heading', { name: 'Not allowed', exact: true })).toBeVisible()
})

test('logged-out visitor to a protected route reaches login', async ({ page }) => {
  // Each test receives an isolated browser context with no saved authentication.
  await page.goto('/admin/allocation')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('button', { name: 'Request OTP', exact: true })).toBeVisible()
})

test('wrong OTP displays the actual backend message', async ({ page }) => {
  await page.goto('/login')
  const valid = await requestOtp(page, phones.CITIZEN)
  // Deterministically wrong, even when the real OTP happens to be 000000.
  const wrong = valid === '000000' ? '000001' : '000000'
  await page.getByLabel('OTP', { exact: true }).fill(wrong)
  const failure = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/otp/verify' && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Verify and log in', exact: true }).click()
  const response = await failure
  // Never inspect a successful verification body (which contains a token).
  expect(response.ok()).toBe(false)
  const body: { message: string } = await response.json()
  expect(typeof body.message).toBe('string')
  expect(body.message.length).toBeGreaterThan(0)
  await expect(page.getByRole('alert')).toContainText(body.message)
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('button', { name: 'Log out', exact: true })).toHaveCount(0)
})
