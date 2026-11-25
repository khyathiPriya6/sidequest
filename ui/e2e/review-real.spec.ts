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

  test('draft stays private through pending review; ownership, ETags and archive remain enforced', async ({ page, browser }) => {
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
    const firstSaved = await page.request.get(`/api/v1/me/quests/${id}`)
    expect(firstSaved.status()).toBe(200)
    const staleEtag = firstSaved.headers().etag
    expect(staleEtag).toBeTruthy()

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
      await expect(page.getByRole('button', { name: 'Publish approved quest' })).toBeDisabled()
      await page.getByRole('button', { name: 'Save changes' }).click()
      await expect(page.getByRole('button', { name: 'Submit for safety review' })).toBeEnabled()
      await expect(page.getByRole('button', { name: 'Publish approved quest' })).toBeDisabled()

      const current = await page.request.get(`/api/v1/me/quests/${id}`)
      expect(current.status()).toBe(200)
      const currentEtag = current.headers().etag
      expect(currentEtag).toBeTruthy()
      expect(currentEtag).not.toBe(staleEtag)
      expect((await current.json()).title).toBe(title)

      const denied = await other.evaluate(async questId => {
        const { csrfToken } = await (await fetch('/api/v1/auth/csrf')).json()
        return (await fetch(`/api/v1/quests/${questId}/archive`, {
          method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken, 'If-Match': '"0"' },
        })).status
      }, id)
      expect(denied).toBe(404)

      const stale = await page.evaluate(async ({ questId, oldEtag }) => {
        const { csrfToken } = await (await fetch('/api/v1/auth/csrf')).json()
        return (await fetch(`/api/v1/quests/${questId}/archive`, {
          method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken, 'If-Match': oldEtag },
        })).status
      }, { questId: id, oldEtag: staleEtag })
      expect(stale).toBe(412)

      await page.getByRole('button', { name: 'Submit for safety review' }).click()
      await expect(page.getByText('Awaiting safety review')).toBeVisible()
      await expect(page.getByRole('button', { name: 'Publish approved quest' })).toBeDisabled()
      const review = await page.request.get(`/api/v1/me/quests/${id}/moderation`)
      expect(review.status()).toBe(200)
      expect((await review.json()).state).toBe('PENDING_REVIEW')

      const prematurePublish = await page.evaluate(async ({ questId, etag }) => {
        const { csrfToken } = await (await fetch('/api/v1/auth/csrf')).json()
        return (await fetch(`/api/v1/quests/${questId}/publish`, {
          method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken, 'If-Match': etag },
        })).status
      }, { questId: id, etag: currentEtag })
      expect(prematurePublish).toBe(409)

      await visitor.goto(`/quests/${id}`)
      await expect(visitor.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()
      await visitor.goto(`/quests?q=${encodeURIComponent(title)}&category=ADVENTURE&difficulty=EASY`)
      await expect(visitor.getByText('No quests match those filters.')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

      await page.goto('/my-quests?status=DRAFT')
      const card = page.getByRole('article', { name: title })
      await expect(card).toBeVisible()
      await card.getByRole('button', { name: 'Archive', exact: true }).click()
      await page.getByRole('button', { name: 'Cancel', exact: true }).click()
      await expect(card).toBeVisible()
      await card.getByRole('button', { name: 'Archive', exact: true }).click()
      await page.getByRole('button', { name: 'Confirm archive' }).click()
      await expect(page.getByText('No draft quests yet.')).toBeVisible()

      await page.goto(`/quests/${id}`)
      await expect(page.getByRole('heading', { name: title })).toBeVisible()
      await expect(page.getByText('75 XP reward')).toBeVisible()
      await visitor.goto(`/quests/${id}`)
      await expect(visitor.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()
      await page.getByRole('button', { name: 'Sign out' }).click()
      await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
    } finally {
      await anonymous.close()
      await otherContext.close()
    }
  })
})
