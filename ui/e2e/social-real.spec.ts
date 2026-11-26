import { expect, test, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'

/** Real isolated browser/API/PostgreSQL only; never run against a developer database. */
test.describe('M5 multi-user social acceptance @auth-real', () => {
  test.skip(process.env.AUTH_E2E !== '1',
    'Run through backend/scripts/test-browser.sh with isolated Postgres and API.')

  async function register(page: Page, username: string) {
    await page.goto('/register')
    await page.getByLabel('Email (required)').fill(`${username}@example.test`)
    await page.getByLabel('Username (required)').fill(username)
    await page.getByLabel('Password (required)').fill('M5-social-test-password-2026')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByText(`@${username}`)).toBeVisible()
  }

  async function csrfPost(page: Page, path: string, input: object) {
    return page.evaluate(async ({ path, input }) => {
      const csrf = await (await fetch('/api/v1/auth/csrf')).json()
      const response = await fetch(path, {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.csrfToken },
        body: JSON.stringify(input),
      })
      return { status: response.status, body: await response.json().catch(() => null) }
    }, { path, input })
  }

  test('private default, consent, friendship, friend-only profile, block and downgrade', async ({ page, browser }) => {
    const suffix = Date.now().toString(36)
    const aliceName = `m5a_${suffix}`, bobName = `m5b_${suffix}`, strangerName = `m5c_${suffix}`
    const bobContext = await browser.newContext()
    const strangerContext = await browser.newContext()
    const bob = await bobContext.newPage(), stranger = await strangerContext.newPage()
    try {
      await register(page, aliceName)
      await register(bob, bobName)
      await register(stranger, strangerName)

      await page.goto('/friends')
      await expect(page.getByRole('heading', { name: 'Friends and social profile' })).toBeVisible()
      await expect(page.getByLabel('Who can see your social profile?')).toHaveValue('PRIVATE')
      const aliceLink = await page.getByRole('link', { name: 'View your social profile' }).getAttribute('href')
      expect(aliceLink).toMatch(/^\/players\/[0-9a-f-]{36}$/)

      // Private is indistinguishable from a non-existent social profile to unrelated accounts.
      await bob.goto(aliceLink!)
      await expect(bob.getByText('This profile is private or unavailable.')).toBeVisible()
      const privateRead = await bob.request.get('/api/v1/social/profiles/' + aliceLink!.split('/').pop())
      const missingRead = await bob.request.get('/api/v1/social/profiles/11111111-1111-4111-8111-111111111111')
      expect(privateRead.status()).toBe(404)
      expect(missingRead.status()).toBe(404)

      await page.getByLabel('Who can see your social profile?').selectOption('FRIENDS')
      await expect(page.getByRole('status').getByText('Privacy updated.')).toBeVisible()
      await bob.reload()
      await expect(bob.getByText('This profile is private or unavailable.')).toBeVisible()

      await bob.goto('/friends')
      await bob.getByLabel('Username').fill(aliceName)
      await bob.getByRole('button', { name: 'Send request' }).click()
      await expect(bob.getByText('Request sent.')).toBeVisible()
      await expect(bob.getByRole('heading', { name: 'Outgoing requests' }).locator('..').getByText(aliceName)).toBeVisible()

      await page.reload()
      const incoming = page.getByRole('heading', { name: 'Incoming requests' }).locator('..')
      await expect(incoming.getByText(bobName)).toBeVisible()
      await incoming.getByRole('button', { name: 'Accept' }).click()
      await expect(page.getByText('Friend request accepted.')).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Your friends' }).locator('..').getByRole('link', { name: bobName })).toBeVisible()

      await bob.goto(aliceLink!)
      await expect(bob.getByRole('heading', { name: aliceName })).toBeVisible()
      await stranger.goto(aliceLink!)
      await expect(stranger.getByText('This profile is private or unavailable.')).toBeVisible()
      const visible = await bob.request.get('/api/v1/social/profiles/' + aliceLink!.split('/').pop())
      expect(visible.status()).toBe(200)
      const exposed = await visible.json()
      expect(Object.keys(exposed).sort()).toEqual(['displayName', 'profileId'])
      expect(exposed.displayName).toBe(aliceName)

      await bob.goto('/friends/leaderboard')
      await expect(bob.getByRole('heading', { name: 'Friends leaderboard' })).toBeVisible()
      await expect(bob.getByText('No eligible XP yet.')).toBeVisible()
      await bob.getByLabel('Ranking period').selectOption('weekly')
      await expect(bob.getByText('No eligible XP yet.')).toBeVisible()

      // Blocking revokes even previously accepted friendship, without revealing profile details.
      await page.goto('/friends')
      await page.getByLabel('Username').fill(bobName)
      await page.getByRole('button', { name: 'Block user' }).click()
      await expect(page.getByText('Account blocked. Existing friendship removed.')).toBeVisible()
      await bob.goto(aliceLink!)
      await expect(bob.getByText('This profile is private or unavailable.')).toBeVisible()
      const blocked = await csrfPost(bob, '/api/v1/me/friend-requests', { username: aliceName })
      expect(blocked.status).toBe(404)
      await bob.goto('/friends')
      await expect(bob.getByRole('heading', { name: 'Your friends' }).locator('..').getByText(aliceName)).toHaveCount(0)

      // Unblocking is not a silent friendship restore: the old pair remains removed.
      const unblock = await csrfPost(page, '/api/v1/me/social/unblocks', { username: bobName })
      expect(unblock.status).toBe(204)
      await bob.goto('/friends')
      await expect(bob.getByRole('heading', { name: 'Your friends' }).locator('..').getByText(aliceName)).toHaveCount(0)
      await bob.goto(aliceLink!)
      await expect(bob.getByText('This profile is private or unavailable.')).toBeVisible()

      // Owner privacy can also be revoked independently of relationship state.
      await page.getByLabel('Who can see your social profile?').selectOption('PRIVATE')
      await expect(page.getByLabel('Who can see your social profile?')).toHaveValue('PRIVATE')

      // Session and CSRF remain enforced on social mutations.
      const noCsrf = await bob.evaluate(async name =>
        (await fetch('/api/v1/me/friend-requests', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: name }),
        })).status, aliceName)
      expect(noCsrf).toBe(403)
      await strangerContext.clearCookies()
      const unauthorized = await stranger.request.get('/api/v1/me/friends')
      expect(unauthorized.status()).toBe(401)
    } finally {
      await bobContext.close()
      await strangerContext.close()
    }
  })

  test('shares an approved published Quest URL without bypassing moderation', async ({ page, browser }) => {
    const suffix = Date.now().toString(36)
    const title = `M5 shareable walk ${suffix}`
    await register(page, `m5q_${suffix}`)
    await page.goto('/quests/new')
    await page.getByRole('textbox', { name: 'Title', exact: true }).fill(title)
    await page.getByRole('textbox', { name: 'Description' }).fill('Walk a safe new route with a friend.')
    await page.getByLabel('Category', { exact: true }).selectOption('ADVENTURE')
    await page.getByLabel('Difficulty', { exact: true }).selectOption('EASY')
    await page.getByRole('spinbutton', { name: 'Reward XP' }).fill('80')
    await page.getByRole('button', { name: 'Save draft' }).click()
    await expect(page).toHaveURL(/\/quests\/[0-9a-f-]+\/edit$/)
    const questId = new URL(page.url()).pathname.split('/')[2]

    // A draft link must not be published to the share UI.
    const guestContext = await browser.newContext()
    const guest = await guestContext.newPage()
    const moderatorContext = await browser.newContext()
    const moderator = await moderatorContext.newPage()
    try {
      await guest.goto(`/quests/${questId}`)
      await expect(guest.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()
      await page.getByRole('button', { name: 'Submit for safety review' }).click()
      await expect(page.getByText('Awaiting safety review')).toBeVisible()

      const moderatorName = `m5mod_${suffix}`
      await register(moderator, moderatorName)
      const response = await moderator.request.get('/api/v1/auth/me')
      expect(response.status()).toBe(200)
      const { id: moderatorId } = await response.json()
      const dbUrl = process.env.PG_E2E_URL, pgBin = process.env.PG_E2E_BIN
      if (!dbUrl || !/^postgresql:\/\/sidequest_test@127\.0\.0\.1:\d+\/sidequest_browser_test$/.test(dbUrl)
        || !pgBin || !/^[0-9a-f-]{36}$/i.test(moderatorId)) {
        throw new Error('Disposable moderation test database not available')
      }
      execFileSync(`${pgBin}/psql`, ['-X', '--set', 'ON_ERROR_STOP=1', '--dbname', dbUrl,
        '-c', `INSERT INTO moderation_staff (user_id) VALUES ('${moderatorId}')`])

      await moderator.goto('/moderation')
      await expect(moderator.getByRole('heading', { name: title })).toBeVisible()
      await moderator.getByRole('button', { name: 'Approve', exact: true }).click()
      const decision = moderator.waitForResponse(r =>
        new URL(r.url()).pathname === `/api/v1/moderation/quests/${questId}/decisions`
        && r.request().method() === 'POST')
      await moderator.getByRole('button', { name: 'Confirm decision' }).click()
      expect((await decision).status()).toBe(200)

      await expect.poll(async () => {
        const review = await page.request.get(`/api/v1/me/quests/${questId}/moderation`)
        return (await review.json()).state
      }).toBe('APPROVED')
      await page.reload()
      await page.getByRole('button', { name: 'Publish approved quest' }).click()
      await page.goto(`/quests/${questId}`)
      await expect(page.getByRole('heading', { name: title })).toBeVisible()
      const shareButton = page.getByRole('button', { name: 'Share this quest' })
      await expect(shareButton).toBeVisible()
      await shareButton.click()
      await expect(page.getByRole('status').getByText(/Quest link copied|Copy this link/)).toBeVisible()
      await guest.goto(`/quests/${questId}`)
      await expect(guest.getByRole('heading', { name: title })).toBeVisible()
      await expect(guest.getByRole('button', { name: 'Sign in to join' })).toBeVisible()
    } finally {
      await guestContext.close()
      await moderatorContext.close()
    }
  })
})
