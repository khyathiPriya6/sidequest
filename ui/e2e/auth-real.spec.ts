import { expect, test } from '@playwright/test'

test.describe('real session authentication @auth-real', () => {
  test.skip(process.env.AUTH_E2E !== '1', 'Set AUTH_E2E=1 with the isolated local backend running.')

  test('registers, restores, rejects invalid CSRF, logs out, signs in, and handles an expired session', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    const suffix = Date.now().toString(36)
    const email = `e2e${suffix}@example.test`
    const username = `e2e_${suffix}`
    const password = 'QuestFlow-2026-password'

    await page.goto('/my-quests?tab=drafts')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
    await page.getByRole('link', { name: 'Create an account' }).click()

    const emailField = page.getByLabel('Email (required)')
    const usernameField = page.getByLabel('Username (required)')
    await emailField.fill(email)
    await usernameField.fill(username)
    await emailField.focus()
    await page.keyboard.press('Tab')
    await expect(usernameField).toBeFocused()
    await usernameField.focus()
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('Password (required)')).toBeFocused()
    await page.getByLabel('Password (required)').fill(password)
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page).toHaveURL(/\/my-quests\?tab=drafts$/)
    await expect(page.getByText(`@${username}`)).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

    const csrfFailure = await page.evaluate(async () => {
      const response = await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'invalid-token' },
        body: '{}',
      })
      return response.status
    })
    expect(csrfFailure).toBe(403)
    await expect(page.getByText(`@${username}`)).toBeVisible()

    await page.reload()
    await expect(page).toHaveURL(/\/my-quests\?tab=drafts$/)
    await expect(page.getByText(`@${username}`)).toBeVisible()
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page).toHaveURL(/\/quests$/)
    await page.getByRole('link', { name: 'Sign in' }).click()
    await page.getByLabel('Email (required)').fill(email)
    await page.getByLabel('Password (required)').fill(password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByText(`@${username}`)).toBeVisible()

    await page.context().clearCookies()
    await page.goto('/my-quests?tab=drafts')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})
