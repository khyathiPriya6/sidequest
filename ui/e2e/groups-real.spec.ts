import { expect, test, type Page } from '@playwright/test'

test.describe('M6 private group journey @auth-real', () => {
  test.skip(process.env.AUTH_E2E !== '1', 'Use backend/scripts/test-browser.sh with isolated PostgreSQL.')

  async function register(page: Page, username: string) {
    await page.goto('/register')
    await page.getByLabel('Email (required)').fill(`${username}@example.test`)
    await page.getByLabel('Username (required)').fill(username)
    await page.getByLabel('Password (required)').fill('M6-group-tests-2026')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByText(`@${username}`)).toBeVisible()
  }

  test('friends can consent to a private team, outsiders cannot access it', async ({ page, browser }) => {
    const suffix = Date.now().toString(36)
    const leaderName=`m6leader_${suffix}`, playerName=`m6player_${suffix}`, outsiderName=`m6other_${suffix}`
    const memberContext=await browser.newContext()
    const outsiderContext=await browser.newContext()
    const member=await memberContext.newPage(), outsider=await outsiderContext.newPage()
    try {
      await register(page,leaderName)
      await register(member,playerName)
      await register(outsider,outsiderName)

      // A team may be created before friendship, but an invitation must require consent.
      await page.goto('/teams')
      await page.getByLabel('Team name (3–80 characters)').fill(`Trail Pack ${suffix}`)
      await page.getByRole('button',{name:'Create team'}).click()
      await expect(page).toHaveURL(/\/teams\/[0-9a-f-]{36}$/)
      const teamUrl = new URL(page.url()).pathname
      await expect(page.getByRole('heading',{name:`Trail Pack ${suffix}`})).toBeVisible()
      await outsider.goto(teamUrl)
      await expect(outsider.getByRole('alert')).toBeVisible()
      const strangerApi=await outsider.request.get('/api/v1'+teamUrl)
      expect(strangerApi.status()).toBe(404)

      await page.getByLabel('Exact username').fill(playerName)
      const deniedInvite = page.waitForResponse(response =>
        new URL(response.url()).pathname === '/api/v1'+teamUrl+'/invitations'
        && response.request().method() === 'POST')
      await page.getByRole('button',{name:'Invite friend'}).click()
      const deniedResponse = await deniedInvite
      expect(deniedResponse.status(), await deniedResponse.text()).toBe(404)

      // Establish accepted friendship through real session-backed UI, not a DB shortcut.
      await page.goto('/friends')
      await page.getByLabel('Username').fill(playerName)
      await page.getByRole('button',{name:'Send request'}).click()
      await expect(page.getByText('Request sent.')).toBeVisible()
      await member.goto('/friends')
      const incoming=member.getByRole('heading',{name:'Incoming requests'}).locator('..')
      await expect(incoming.getByText(leaderName)).toBeVisible()
      await incoming.getByRole('button',{name:'Accept'}).click()
      await expect(member.getByText('Friend request accepted.')).toBeVisible()

      await page.goto(teamUrl)
      await page.getByLabel('Exact username').fill(playerName)
      await page.getByRole('button',{name:'Invite friend'}).click()
      await expect(page.getByText('Team invitation sent.')).toBeVisible()
      await member.goto('/teams')
      await expect(member.getByText(`Trail Pack ${suffix}`)).toBeVisible()
      await member.getByRole('heading',{name:'Team invitations'}).locator('..')
        .getByRole('button',{name:'Accept'}).click()
      await expect(member.getByText('Joined team.')).toBeVisible()
      await member.goto(teamUrl)
      await expect(member.getByRole('heading',{name:`Trail Pack ${suffix}`})).toBeVisible()
      await expect(member.getByRole('heading',{name:'Team members'}).locator('..').getByText(leaderName)).toBeVisible()
      await expect(member.getByRole('heading',{name:'Team leaderboard'})).toBeVisible()
      await expect(member.getByText('No group challenges yet.')).toBeVisible()

      // A guest must sign in; an unrelated authenticated account still cannot see roster.
      await outsider.goto(teamUrl)
      await expect(outsider.getByRole('alert')).toBeVisible()
      const roster=await outsider.request.get('/api/v1'+teamUrl+'/members')
      expect(roster.status()).toBe(404)
      const guestContext=await browser.newContext()
      try {
        const guest=await guestContext.newPage()
        await guest.goto(teamUrl)
        await expect(guest).toHaveURL(/\/login$/)
      } finally { await guestContext.close() }

      // Leaving revokes access without changing another user's personal XP.
      member.once('dialog', dialog => void dialog.accept())
      await member.getByRole('button',{name:'Leave team'}).click()
      await expect(member).toHaveURL('/teams')
      await member.goto(teamUrl)
      await expect(member.getByRole('alert')).toBeVisible()
    } finally {
      await memberContext.close()
      await outsiderContext.close()
    }
  })
})
