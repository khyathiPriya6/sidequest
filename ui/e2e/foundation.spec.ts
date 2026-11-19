import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test.beforeEach(async ({ page }) => {
  // Component/browser tests use a deterministic health response; live proxy is checked separately.
  await page.route('**/api/v1/health', route => route.fulfill({ json: { status: 'UP', service: 'sidequest-api' } }))
  await page.route('**/api/v1/quests**', route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/quests/quest-e2e') {
      return route.fulfill({ json: {
        id: 'quest-e2e', creatorId: 'creator-e2e', title: 'Explore a nearby trail',
        description: 'Take a walk along a route you have not tried before.',
        category: 'ADVENTURE', difficulty: 'EASY', rewardXp: 50, status: 'PUBLISHED',
        createdAt: '2025-01-01T10:00:00Z', updatedAt: '2025-01-01T10:00:00Z',
        publishedAt: '2025-01-01T10:00:00Z', archivedAt: null,
      } })
    }
    return route.fulfill({ json: {
      content: [{
        id: 'quest-e2e', creatorId: 'creator-e2e', title: 'Explore a nearby trail',
        description: 'Take a walk along a route you have not tried before.',
        category: 'ADVENTURE', difficulty: 'EASY', rewardXp: 50, status: 'PUBLISHED',
        createdAt: '2025-01-01T10:00:00Z', updatedAt: '2025-01-01T10:00:00Z',
        publishedAt: '2025-01-01T10:00:00Z', archivedAt: null,
      }],
      page: Number(url.searchParams.get('page') ?? 0), size: 20,
      totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
    } })
  })
})

for (const width of [320, 390, 768, 1440]) {
  test(`shell and component preview fit ${width}px and expose usable navigation`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/quests')
    await expect(page.getByRole('heading', { name: 'Make today a quest.' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible({ visible: width >= 1024 })
    await page.screenshot({ path: testInfo.outputPath(`explore-${width}.png`), fullPage: true })
    await page.goto('/dev/components')
    await page.getByRole('button', { name: 'Preview member navigation' }).click()
    const nav = page.getByRole('navigation', { name: width >= 1024 ? 'Main navigation' : 'Mobile navigation' })
    await expect(nav).toBeVisible()
    for (const link of await nav.getByRole('link').all()) {
      const bounds = await link.boundingBox()
      expect(bounds!.height).toBeGreaterThanOrEqual(44)
      expect(bounds!.width).toBeGreaterThanOrEqual(44)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.getByRole('button', { name: 'Open sample dialog' }).scrollIntoViewIfNeeded()
    const trigger = await page.getByRole('button', { name: 'Open sample dialog' }).boundingBox()
    const navBounds = await nav.boundingBox()
    if (width < 1024) expect(trigger!.y + trigger!.height).toBeLessThanOrEqual(navBounds!.y)
    await page.screenshot({ path: testInfo.outputPath(`components-${width}.png`), fullPage: true })
  })
}

test('dialog contains focus, blocks background interaction, and restores focus on dismissal', async ({ page }) => {
  await page.goto('/dev/components')
  const trigger = page.getByRole('button', { name: 'Open sample dialog' })
  await trigger.click()
  const dialog = page.getByRole('dialog', { name: 'Review a sample dialog' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: 'Close dialog' })).toBeFocused()
  await expect(page.locator('body')).toHaveCSS('overflow', 'hidden')
  await page.keyboard.press('Shift+Tab')
  await expect(dialog.getByRole('button', { name: 'Done' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog.getByRole('button', { name: 'Close dialog' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(trigger).toBeFocused()
  await expect(page.locator('body')).not.toHaveCSS('overflow', 'hidden')
  await trigger.click()
  await dialog.getByRole('button', { name: 'Close dialog' }).click()
  await expect(trigger).toBeFocused()
})

test('deep links refresh, static routes win, and history restores the route', async ({ page }) => {
  await page.goto('/quests/new')
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Create a quest', exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Back to Explore' }).click()
  await expect(page).toHaveURL(/\/quests$/)
  await expect(page.getByRole('heading', { name: 'Make today a quest.' })).toBeVisible()
  await expect(page.getByRole('main')).toBeFocused()
  await page.goBack()
  await expect(page.getByRole('heading', { name: 'Create a quest', exact: true })).toBeVisible()
  await expect(page.getByRole('main')).toBeFocused()
  await expect(page).toHaveTitle('Create a quest · SideQuest')
  await page.goto('/does-not-exist')
  await expect(page.getByRole('heading', { name: 'That page isn’t here.' })).toBeVisible()
})

test('public quest details are reachable by keyboard and retain the Explore filters on return', async ({ page }) => {
  await page.goto('/quests?q=trail&category=ADVENTURE')
  const questLink = page.getByRole('link', { name: 'Explore a nearby trail' })
  await expect(questLink).toBeVisible()
  await questLink.focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/quests\/quest-e2e\?q=trail&category=ADVENTURE/)
  await expect(page.getByRole('heading', { name: 'Explore a nearby trail' })).toBeVisible()
  await page.getByRole('link', { name: 'Back to Explore' }).click()
  await expect(page).toHaveURL(/\/quests\?q=trail&category=ADVENTURE/)
  await expect(page.getByRole('searchbox', { name: 'Search quests' })).toHaveValue('trail')
})

test('reduced motion and doubled text remain usable', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 768, height: 900 })
  await page.goto('/dev/components')
  await page.addStyleTag({ content: 'html { font-size: 200%; }' })
  await page.getByRole('button', { name: 'Preview member navigation' }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  const spinner = page.getByRole('button', { name: 'Saving sample' }).locator('span')
  await expect(spinner).toHaveCSS('animation-name', 'none')
  await page.getByRole('button', { name: 'Open sample dialog' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Escape')
})

test('foundation screens and modal have no automated WCAG AA violations', async ({ page }) => {
  for (const path of ['/quests', '/dev/components', '/login']) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
    expect(results.violations).toEqual([])
  }
  await page.goto('/dev/components')
  await page.getByRole('button', { name: 'Open sample dialog' }).click()
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
  expect(results.violations).toEqual([])
})
