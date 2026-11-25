import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

// UI-only fixtures; the live moderation-to-reward journey is participation-real.spec.ts.
for (const width of [320, 390, 768, 1440]) {
  test(`leaderboard and profile remain accessible at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const player = { username: 'a_long_player_name_12345678901', rank: 1, earnedXp: 75 }
    await page.route('**/api/v1/**', route => {
      const url = new URL(route.request().url())
      const path = url.pathname
      if (path.endsWith('/auth/csrf')) return route.fulfill({ json: { csrfToken: 'test-token' } })
      if (path.endsWith('/auth/me')) return route.fulfill({ json: {
        id: '00000000-0000-4000-8000-000000000001', username: player.username, email: 'ui@example.test',
      } })
      if (path.endsWith('/health')) return route.fulfill({ json: { status: 'UP', service: 'sidequest-api' } })
      if (path.endsWith('/leaderboard-rank')) return route.fulfill({ json: { period: url.searchParams.get('period'), player } })
      if (path.endsWith('/leaderboard')) return route.fulfill({ json: {
        period: url.searchParams.get('period'), content: [player], page: 0, size: 20,
        totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      } })
      if (path.endsWith('/streak')) return route.fulfill({ json: { currentStreak: 1, longestStreak: 3, lastCompletionDate: '2026-11-25', timezone: 'UTC' } })
      if (path.endsWith('/profile')) return route.fulfill({ json: {
        username: player.username, createdAt: '2026-11-25T00:00:00Z', totalXp: 75, level: 1, xpIntoLevel: 75,
        xpRemaining: 425, progressPercent: 15, joinedQuests: 1, activeQuests: 0, completedQuests: 1, createdQuests: 0, unlockedAchievementCount: 1,
      } })
      if (path.endsWith('/achievements')) return route.fulfill({ json: { achievements: [{ id: 'first_adventure', name: 'First Adventure', description: 'Complete any Quest.', progress: 1, target: 1, unlocked: true, unlockedAt: '2026-11-25T00:00:00Z' }] } })
      if (path.endsWith('/xp-transactions')) return route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false } })
      return route.fulfill({ status: 404, json: {} })
    })
    await page.goto('/leaderboard')
    await expect(page.getByText('Rank 1 · 75 eligible XP')).toBeVisible()
    await page.getByRole('button', { name: 'Weekly' }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('table')).toHaveAccessibleName('Weekly player rankings')
    const nav = page.getByRole('navigation', { name: width < 1024 ? 'Mobile navigation' : 'Main navigation' })
    const link = nav.getByRole('link', { name: 'Leaderboard' })
    await expect(link).toHaveAttribute('aria-current', 'page')
    for (const target of await nav.getByRole('link').all()) {
      const bounds = await target.boundingBox()
      expect(bounds!.width).toBeGreaterThanOrEqual(44)
      expect(bounds!.height).toBeGreaterThanOrEqual(44)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
    await page.screenshot({ path: testInfo.outputPath(`leaderboard-${width}.png`), fullPage: true })
    await nav.getByRole('link', { name: 'Profile' }).click()
    await expect(page.getByText('Level 1 · 75 XP total')).toBeVisible()
    await expect(page.getByText('3 days', { exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
    await page.screenshot({ path: testInfo.outputPath(`profile-${width}.png`), fullPage: true })
  })
}
