import { test, expect, type Page } from '@playwright/test'

test.describe('M6 group teams @auth-real', () => {
  test.skip(process.env.AUTH_E2E !== '1',
    'Only run through backend/scripts/test-browser.sh on isolated PostgreSQL.')

  async function register(page: Page, username: string) {
    await page.goto('/register')
    await page.getByLabel('Email (required)').fill(username+'@example.test')
    await page.getByLabel('Username (required)').fill(username)
    await page.getByLabel('Password (required)').fill('M6-group-challenge-test-2026')
    await page.getByRole('button',{name:'Create account'}).click()
    await expect(page.getByText('@'+username)).toBeVisible()
  }
  test('owner invites member, unrelated user cannot inspect team, member accepts',async({page,browser})=>{
    const suffix=Date.now().toString(36)
    const ownerName='m6owner_'+suffix,memberName='m6member_'+suffix,otherName='m6other_'+suffix
    const memberCtx=await browser.newContext(),otherCtx=await browser.newContext()
    const member=await memberCtx.newPage(),other=await otherCtx.newPage()
    try {
      await register(page,ownerName)
      await register(member,memberName)
      await register(other,otherName)
      await page.goto('/teams')
      await expect(page.getByRole('heading',{name:'Teams and group challenges'})).toBeVisible()
      await page.getByLabel('Team name').fill('Trail Explorers')
      await page.getByRole('button',{name:'Create team'}).click()
      await expect(page.getByRole('link',{name:'Trail Explorers'})).toBeVisible()
      await page.getByRole('link',{name:'Trail Explorers'}).click()
      await expect(page.getByRole('heading',{name:'Trail Explorers'})).toBeVisible()
      const teamPath=new URL(page.url()).pathname
      await other.goto(teamPath)
      await expect(other.getByRole('alert')).toContainText(/unavailable|not found/i)
      const unauthorized=await other.request.get('/api/v1'+teamPath)
      expect(unauthorized.status()).toBe(404)

      await page.getByLabel('Username').fill(memberName)
      await page.getByRole('button',{name:'Send invitation'}).click()
      await expect(page.getByText('Invitation sent.')).toBeVisible()
      await member.goto('/teams')
      const inbox=member.getByRole('heading',{name:'Incoming team invitations'}).locator('..')
      await expect(inbox.getByText('Trail Explorers')).toBeVisible()
      await inbox.getByRole('button',{name:'Accept'}).click()
      await expect(member.getByText('Team invitation accepted.')).toBeVisible()
      await expect(member.getByRole('link',{name:'Trail Explorers'})).toBeVisible()

      await member.getByRole('link',{name:'Trail Explorers'}).click()
      const members=member.getByRole('heading',{name:'Team members'}).locator('..')
      await expect(members.getByText(ownerName)).toBeVisible()
      await expect(members.getByText(memberName)).toBeVisible()
      await expect(member.getByRole('heading',{name:'Team leaderboard'})).toBeVisible()
      await expect(member.getByRole('heading',{name:'Create group challenge'})).toHaveCount(0)
      await page.reload()
      await expect(page.getByText('2 members')).toBeVisible()
    } finally {
      await memberCtx.close()
      await otherCtx.close()
    }
  })
})
