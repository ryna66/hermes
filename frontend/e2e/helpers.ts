import { open, stat } from 'node:fs/promises'
import { expect } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'

export const phones = {
  CITIZEN: '9000000000', ADMIN: '9000000001', UNIVERSITY: '9000000002',
  INDUSTRY: '9000000003', GOVERNMENT: '9000000004',
} as const

// Only inspect bytes appended after this UI request, like scripts/e2e.py.
// Poll a boolean so assertion diagnostics never include the code or log contents.
export async function requestOtp(page: Page, phone: string): Promise<string> {
  await page.getByLabel('Phone', { exact: true }).fill(phone)
  const logPath = process.env.SIH_CORE_LOG || '/tmp/sih-core.log'
  const before = await stat(logPath)
  const offset = before.size
  await page.getByRole('button', { name: 'Request OTP', exact: true }).click()
  await expect(page.getByLabel('OTP', { exact: true })).toBeVisible()
  let code = ''
  await expect.poll(async () => {
    const file = await open(logPath, 'r')
    try {
      const current = await file.stat()
      if (current.ino !== before.ino || current.size < offset) {
        throw new Error('Core log was rotated or truncated during OTP login; rerun with a stable log.')
      }
      const bytes = Buffer.alloc(current.size - offset)
      await file.read(bytes, 0, bytes.length, offset)
      const matches = [...bytes.toString('utf8').matchAll(new RegExp(`Development OTP for ${phone}: (\\d{6})(?!\\d)`, 'g'))]
      code = matches.at(-1)?.[1] || ''
      return code.length === 6
    } finally { await file.close() }
  }, { timeout: 15_000, intervals: [100, 200, 500], message: 'Expected a fresh dev OTP appended to SIH_CORE_LOG after the UI request' }).toBe(true)
  return code
}

export async function login(page: Page, role: keyof typeof phones) {
  await page.goto('/login')
  const logout = page.getByRole('button', { name: 'Log out', exact: true })
  // Waiting for either branch also handles the restored account lookup on reload.
  await expect(logout.or(page.getByRole('button', { name: 'Request OTP', exact: true }))).toBeVisible()
  if (await logout.isVisible()) {
    await logout.click()
    await page.goto('/login')
    await expect(page.getByRole('button', { name: 'Request OTP', exact: true })).toBeVisible()
  }
  const code = await requestOtp(page, phones[role])
  await page.getByLabel('OTP', { exact: true }).fill(code)
  await page.getByRole('button', { name: 'Verify and log in', exact: true }).click()
  await expect(page.locator('header')).toContainText(`(${role})`)
  await expect(page.getByRole('button', { name: 'Log out', exact: true })).toBeVisible()
}

export function uniqueTitle(label = 'Contaminated borewell water in Gumla') {
  return `${label} ${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export async function submitReport(page: Page, title: string, latitude = '23.044') {
  await page.goto('/submit')
  await page.getByLabel('Title', { exact: true }).fill(title)
  await page.getByLabel('Description', { exact: true }).fill('Unsafe borewell drinking water in Gumla poses a public health risk. Villagers need water testing and filtration.')
  await page.getByLabel('District', { exact: true }).fill('Gumla')
  await page.getByLabel('Affected people', { exact: true }).fill('5')
  // Manual coordinate fields are a supported UI location selection; no geolocation
  // permission or external tile-server dependency is needed.
  await page.getByLabel('Latitude', { exact: true }).fill(latitude)
  await page.getByLabel('Longitude', { exact: true }).fill('84.542')
  await page.getByRole('button', { name: 'Submit challenge', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Challenge submitted', exact: true })).toBeVisible()
  const href = await page.getByRole('link', { name: 'View your challenge', exact: true }).getAttribute('href')
  if (!href?.match(/^\/challenges\/[0-9a-f-]+$/i)) throw new Error('Submission did not expose a challenge detail link')
  return href
}

// The two duplicate reports intentionally share identical title/description.
// The title isolates this run; the UI-provided href distinguishes the pair.
export async function findReport(page: Page, title: string, href: string, kind: 'admin' | 'inbox' | 'own'): Promise<Locator> {
  const main = page.getByRole('main')
  const next = main.getByRole('button', { name: 'Next', exact: true })
  for (let number = 1; ; number++) {
    await expect(main.getByText(new RegExp(`^Page ${number}(?: of|;)`))).toBeVisible()
    const link = page.getByRole('link', { name: title, exact: true }).and(page.locator(`a[href="${href}"]`))
    if (await link.count()) {
      await expect(link).toBeVisible()
      return kind === 'own' ? main.getByRole('listitem').filter({ has: link }) : main.getByRole('article').filter({ has: link })
    }
    if (!(await next.isEnabled())) throw new Error(`Report from this run was not found in ${kind} pages`)
    await next.click()
  }
}

export async function detailValue(page: Page, label: string, value: string) {
  await expect(page.locator('dt').filter({ hasText: new RegExp(`^${label}$`) }).locator('xpath=following-sibling::dd[1]')).toHaveText(value)
}

export async function move(page: Page, target: string, reason?: string) {
  await page.getByRole('button', { name: `Move to ${target}`, exact: true }).click()
  const form = page.locator('form').filter({ has: page.getByRole('heading', { name: `Move to ${target}`, exact: true }) })
  if (reason) await form.getByLabel('Reason (required)', { exact: true }).fill(reason)
  await form.getByRole('button', { name: 'Submit action', exact: true }).click()
  await expect(form).toHaveCount(0)
}
