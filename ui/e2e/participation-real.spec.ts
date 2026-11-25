import { expect, test, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import type { Browser } from '@playwright/test'

// Uses the repository's isolated backend/scripts/test-browser.sh harness.
// Never contacts or migrates a developer database.
test.describe('E04 real participation and progression @auth-real', () => {
  test.skip(process.env.AUTH_E2E !== '1', 'Run with backend/scripts/test-browser.sh against an isolated PostgreSQL database.')

  async function register(page: Page, username: string) {
    await page.goto('/register')
    await page.getByLabel('Email (required)').fill(`${username}@example.test`)
    await page.getByLabel('Username (required)').fill(username)
    await page.getByLabel('Password (required)').fill('E04-quest-integration-2026')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByText(`@${username}`)).toBeVisible()
  }

  async function createQuest(
  page: Page,
  browser: Browser,
  title: string,
  reward = 75,
) {
  await page.goto('/quests/new')
  await page.getByRole('textbox', {
    name: 'Title', exact: true,
  }).fill(title)

  await page.getByRole('textbox', {
    name: 'Description',
  }).fill('Walk a new route and describe what you discover.')

  await page.getByLabel('Category', {
    exact: true,
  }).selectOption('ADVENTURE')

  await page.getByLabel('Difficulty', {
    exact: true,
  }).selectOption('EASY')

  await page.getByRole('spinbutton', {
    name: 'Reward XP',
  }).fill(String(reward))

  await page.getByRole('button', {
    name: 'Save draft',
  }).click()

  await expect(page).toHaveURL(
    /\/quests\/[0-9a-f-]+\/edit$/,
  )

  const id = new URL(page.url()).pathname.split('/')[2]

  // Submit through the real creator UI.
  await page.getByRole('button', {
    name: 'Submit for safety review',
  }).click()

  await expect(page.getByText(
    'Awaiting safety review',
  )).toBeVisible()

  await expect(page.getByRole('button', {
    name: 'Publish approved quest',
  })).toBeDisabled()

  // Register a separate test moderator.
  const moderatorContext = await browser.newContext()
  const moderator = await moderatorContext.newPage()

  try {
    const username = `moderator_${Date.now().toString(36)}`
    await register(moderator, username)

    const identity = await moderator.request.get('/api/v1/auth/me')
    expect(identity.status()).toBe(200)
    const { id: moderatorId } = await identity.json()

    // Only permit moderator provisioning in the disposable
    // database created by test-browser.sh.
    const dbUrl = process.env.PG_E2E_URL
    const pgBin = process.env.PG_E2E_BIN

    if (
      !dbUrl ||
      !/^postgresql:\/\/sidequest_test@127\.0\.0\.1:\d+\/sidequest_browser_test$/.test(dbUrl) ||
      !pgBin ||
      !/^[0-9a-f-]{36}$/i.test(moderatorId)
    ) {
      throw new Error('Isolated moderator test setup unavailable')
    }

    execFileSync(`${pgBin}/psql`, [
      '-X',
      '--set', 'ON_ERROR_STOP=1',
      '--dbname', dbUrl,
      '-c', `INSERT INTO moderation_staff (user_id)
             VALUES ('${moderatorId}')`,
    ])

    // Approve through the actual moderator UI.
    await moderator.goto('/moderation')

    await expect(moderator.getByRole('heading', {
      name: title,
    })).toBeVisible()

    await moderator.getByRole('button', {
      name: 'Approve',
      exact: true,
    }).click()

    const approvalResponsePromise = moderator.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/v1/moderation/quests/${id}/decisions`
      && response.request().method() === 'POST',
    )
    await moderator.getByRole('button', {
      name: 'Confirm decision',
    }).click()
    const approvalResponse = await approvalResponsePromise
    expect(approvalResponse.status()).toBe(200)
    expect(await approvalResponse.json()).toMatchObject({ state: 'APPROVED' })

    // Confirm the committed decision through the creator's authenticated session.
    await expect.poll(async () => {
      const review = await page.request.get(`/api/v1/me/quests/${id}/moderation`)
      expect(review.status()).toBe(200)
      return (await review.json()).state
    }, { message: 'Creator should see the approved moderation decision' }).toBe('APPROVED')

    // The editor holds a snapshot; reload only after the decision is available.
    await page.reload()

    await expect(page.getByText(
      'Approved for publishing',
    )).toBeVisible()

    const publishButton = page.getByRole('button', {
      name: 'Publish approved quest',
    })
    await expect(publishButton).toBeEnabled()
    await publishButton.click()

    await expect(page.getByRole('heading', {
      name: title,
    })).toBeVisible()

    return id
  } finally {
    await moderatorContext.close()
  }
}

  test('creator publishes, participant joins, completes once, and profile survives reload', async ({ page, browser }) => {
    const suffix = Date.now().toString(36)
    const title = `E04 trail ${suffix}`
    await register(page, `creator_${suffix}`)
    const id = await createQuest(page, browser, title)

    // Creator may see public details but must not be able to join.
    await page.goto(`/quests/${id}`)
    await expect(page.getByText(/creators cannot join/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Join quest' })).toHaveCount(0)

    const guestContext = await browser.newContext()
    const guest = await guestContext.newPage()
    const participantContext = await browser.newContext()
    const participant = await participantContext.newPage()
    const otherContext = await browser.newContext()
    const other = await otherContext.newPage()
    try {
      await guest.goto(`/quests/${id}`)
      await expect(guest.getByRole('button', { name: 'Sign in to join' })).toBeVisible()
      await guest.getByRole('button', { name: 'Sign in to join' }).click()
      await expect(guest).toHaveURL(/\/login$/)

      await register(participant, `player_${suffix}`)
      await participant.goto(`/quests/${id}`)
      await participant.getByRole('button', { name: 'Join quest' }).click()
      await expect(participant.getByText('In Progress')).toBeVisible()
      await participant.goto('/my-quests?tab=in-progress')
      await expect(participant.getByRole('heading', { name: title })).toBeVisible()

      const recordLink = participant.getByRole('link', { name: title })
      const recordPath = await recordLink.getAttribute('href')
      expect(recordPath).toMatch(/^\/participations\//)
      const recordId = recordPath!.split('/')[2]

      // Another player cannot read or complete somebody else's participation.
      await register(other, `another_${suffix}`)
      const unauthorizedRead = await other.request.get(`/api/v1/me/participations/${recordId}`)
      expect(unauthorizedRead.status()).toBe(404)
      const unauthorizedWrite = await other.evaluate(async participationId => {
        const token = await (await fetch('/api/v1/auth/csrf')).json()
        return (await fetch(`/api/v1/participations/${participationId}/complete`, {
          method: 'POST', headers: { 'X-CSRF-TOKEN': token.csrfToken },
        })).status
      }, recordId)
      expect(unauthorizedWrite).toBe(404)

      // Original reward remains available even after the creator archives.
      await page.goto('/my-quests?status=PUBLISHED')
      const card = page.getByRole('article', { name: title })
      await card.getByRole('button', { name: 'Archive', exact: true }).click()
      await page.getByRole('button', { name: 'Confirm archive' }).click()

      await guest.goto(`/quests/${id}`)
      await expect(guest.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()
      await other.goto(`/quests/${id}`)
      await expect(other.getByRole('heading', { name: 'Quest unavailable' })).toBeVisible()

      await participant.goto(recordPath!)
      await expect(participant.getByText(/Archived quest|Archived/i).first()).toBeVisible()
      await participant.getByRole('button', { name: 'Complete quest' }).click()
      const dialog = participant.getByRole('dialog', { name: 'Complete this quest?' })
      await dialog.getByRole('button', { name: 'Confirm completion' }).click()
      await expect(participant.getByText(/Completed · 75 XP awarded/)).toBeVisible()
      await expect(participant.getByRole('button', { name: 'Complete quest' })).toHaveCount(0)

      await participant.goto('/my-quests?tab=completed')
      await expect(participant.getByRole('heading', { name: title })).toBeVisible()
      await participant.goto('/profile')
      await expect(participant.getByText('Level 1 · 75 XP total')).toBeVisible()
      await expect(participant.getByText(/75 XP into this level/)).toBeVisible()
      await expect(participant.getByText(title)).toBeVisible()
      await participant.reload()
      await expect(participant.getByText('Level 1 · 75 XP total')).toBeVisible()

      const retry = await participant.evaluate(async participationId => {
        const { csrfToken } = await (await fetch('/api/v1/auth/csrf')).json()
        const result = await fetch(`/api/v1/participations/${participationId}/complete`, {
          method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken },
        })
        return { status: result.status, body: await result.json() }
      }, recordId)
      expect(retry.status).toBe(200)
      expect(retry.body.awardedXp).toBe(75)
      await participant.reload()
      await expect(participant.getByText('Level 1 · 75 XP total')).toBeVisible()
    } finally {
      await guestContext.close()
      await participantContext.close()
      await otherContext.close()
    }
  })
})
