import { expect, test, type Page } from '@playwright/test'

test.describe('integrated review regression @auth-real', () => {
  test.skip(process.env.AUTH_E2E !== '1', 'Use backend/scripts/test-browser.sh for an isolated real backend.')

  async function register(page: Page, name: string) {
    await page.goto('/register')
    await page.getByLabel('Email (required)').fill(`${name}@example.test`)
    await page.getByLabel('Username (required)').fill(name)
    await page.getByLabel('Password (required)').fill('Review-session-password')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByText(`@${name}`)).toBeVisible()
  }

  test('saved draft publishes actual content; private and archived details enforce ownership', async ({ page, browser }) => {
    const suffix = Date.now().toString(36)
    await page.setViewportSize({ width: 390, height: 844 })
    await register(page, `owner_${suffix}`)
    await page.goto('/quests/new')
    await page.getByRole('textbox', { name: 'Title', exact: true }).fill(`Review trail ${suffix}`)
    await page.getByRole('textbox', { name: 'Description' }).fill('Walk a different route and describe the places you discover.')
    await page.getByLabel('Category', { exact: true }).selectOption('ADVENTURE')
    await page.getByLabel('Difficulty', { exact: true }).selectOption('EASY')
    await page.getByRole('spinbutton', { name: 'Reward XP' }).fill('75')
    await page.getByRole('button', { name: 'Save draft' }).click()
    await expect(page).toHaveURL(/\/quests\/[0-9a-f-]+\/edit$/)
    const id = new URL(page.url()).pathname.split('/')[2]
    const anonymous = await browser.newContext()
    const visitor = await anonymous.newPage()
    const otherContext = await browser.newContext()
    const other = await otherContext.newPage()
    try {
      await visitor.goto(`/quests/${id}`)
      await expect(visitor.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()
      await register(other, `other_${suffix}`)
      await other.goto(`/quests/${id}`)
      await expect(other.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()

      const title = `Saved revision ${suffix}`
      await page.getByRole('textbox', { name: 'Title', exact: true }).fill(title)
      await expect(page.getByRole('button', { name: 'Publish quest' })).toBeDisabled()
      await page.getByRole('button', { name: 'Save changes' }).click()
      await expect(page.getByRole('button', { name: 'Publish quest' })).toBeEnabled()
      const saved = await page.request.get(`/api/v1/me/quests/${id}`)
      const etag = saved.headers().etag
      await page.getByRole('button', { name: 'Publish quest' }).click()
      await expect(page.getByRole('heading', { name: title })).toBeVisible()
      await expect(page.getByText('Walk a different route and describe the places you discover.')).toBeVisible()
      await expect(page.getByText('75 XP reward')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

      await visitor.goto(`/quests?q=${encodeURIComponent(title)}&category=ADVENTURE&difficulty=EASY`)
      await visitor.getByRole('link', { name: title }).click()
      await expect(visitor.getByText('75 XP reward')).toBeVisible()
      const denied = await other.evaluate(async questId => {
        const { csrfToken } = await (await fetch('/api/v1/auth/csrf')).json()
        return (await fetch(`/api/v1/quests/${questId}/archive`, { method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken, 'If-Match': '"0"' } })).status
      }, id)
      expect(denied).toBe(404)
      const stale = await page.evaluate(async ({ questId, oldEtag }) => {
        const { csrfToken } = await (await fetch('/api/v1/auth/csrf')).json()
        return (await fetch(`/api/v1/quests/${questId}/archive`, { method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken, 'If-Match': oldEtag } })).status
      }, { questId: id, oldEtag: etag })
      expect(stale).toBe(412)

      await page.goto('/my-quests?status=PUBLISHED')
      const card = page.getByRole('article', { name: title })
      await card.getByRole('button', { name: 'Archive', exact: true }).click()
      await page.getByRole('button', { name: 'Cancel', exact: true }).click()
      await expect(card).toBeVisible()
      await card.getByRole('button', { name: 'Archive', exact: true }).click()
      await page.getByRole('button', { name: 'Confirm archive' }).click()
      await expect(page.getByText('No published quests yet.')).toBeVisible()
      await page.goto(`/quests/${id}`)
      await expect(page.getByText('75 XP reward')).toBeVisible()
      await visitor.goto(`/quests/${id}`)
      await expect(visitor.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()
      await visitor.goto(`/quests?q=${encodeURIComponent(title)}`)
      await expect(visitor.getByText('No quests match those filters.')).toBeVisible()
      await page.getByRole('button', { name: 'Sign out' }).click()
      await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
    } finally { await anonymous.close(); await otherContext.close() }
  })
})
