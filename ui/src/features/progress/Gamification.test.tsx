import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { LeaderboardPage } from './LeaderboardPage'
import { GamificationSummary } from './GamificationSummary'
import { getLeaderboard, getRanking, getStreak } from './gamificationApi'

const player = { username: 'explorer', rank: 1, earnedXp: 75 }
let fail = false
let empty = false
beforeEach(() => {
  fail = false; empty = false
  vi.stubGlobal('fetch', vi.fn(async input => {
    const url = new URL(String(input), 'http://localhost')
    if (fail) return Response.json({ detail: 'Service unavailable' }, { status: 503 })
    if (url.pathname.endsWith('/streak')) return Response.json({ currentStreak: 3, longestStreak: 7, lastCompletionDate: '2026-11-25', timezone: 'UTC' })
    if (url.pathname.endsWith('/leaderboard-rank')) return Response.json({ period: url.searchParams.get('period'), player: empty ? null : player })
    const page = Number(url.searchParams.get('page'))
    return Response.json({ period: url.searchParams.get('period'), content: empty ? [] : [player], page, size: 20,
      totalElements: empty ? 0 : 21, totalPages: empty ? 0 : 2, hasNext: !empty && page === 0, hasPrevious: page > 0 })
  }))
})
it('loads rankings, filters periods, resets pagination and preserves selected-period clicks', async () => {
  const user = userEvent.setup()
  render(<LeaderboardPage />)
  expect(screen.getByRole('status')).toHaveTextContent('Loading leaderboard')
  expect(await screen.findByText('Rank 1 · 75 eligible XP')).toBeVisible()
  expect(within(screen.getByRole('table')).getByRole('rowheader')).toHaveTextContent('explorer (you)')
  await user.click(screen.getByRole('button', { name: 'Next' }))
  expect(await screen.findByText('Page 2 of 2')).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Weekly' }))
  expect(await screen.findByText('Weekly player rankings')).toBeVisible()
  expect(screen.getByText('Page 1 of 2')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Weekly' })).toHaveAttribute('aria-pressed', 'true')
  await user.click(screen.getByRole('button', { name: 'Weekly' }))
  expect(screen.getByRole('table')).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Monthly' }))
  expect(await screen.findByText('Monthly player rankings')).toBeVisible()
  expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('period=weekly&page=0'))).toBe(true)
})
it('shows empty and zero-XP rank states without invented scores', async () => {
  empty = true
  render(<LeaderboardPage />)
  expect(await screen.findByText(/Unranked this period/)).toBeVisible()
  expect(screen.getByText(/No ranked players/)).toBeVisible()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})
it('offers explicit retry after failure', async () => {
  fail = true
  const user = userEvent.setup()
  render(<LeaderboardPage />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable')
  fail = false
  await user.click(screen.getByRole('button', { name: 'Retry leaderboard' }))
  expect(await screen.findByRole('table')).toBeVisible()
})
it('renders streak and rank in the profile with a leaderboard link', async () => {
  render(<MemoryRouter><GamificationSummary /></MemoryRouter>)
  expect(await screen.findByText('3 days')).toBeVisible()
  expect(screen.getByText('7 days')).toBeVisible()
  expect(screen.getByText('Rank 1')).toBeVisible()
  expect(screen.getByText(/2026-11-25/)).toBeVisible()
  expect(screen.getByRole('link', { name: 'View leaderboard' })).toHaveAttribute('href', '/leaderboard')
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))
})
it('retries profile metrics independently', async () => {
  fail = true
  const user = userEvent.setup()
  render(<MemoryRouter><GamificationSummary /></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load streak and rank')
  fail = false
  await user.click(screen.getByRole('button', { name: 'Retry streak and rank' }))
  expect(await screen.findByText('3 days')).toBeVisible()
})
it('rejects malformed ranks, page metadata and streaks', async () => {
  vi.mocked(fetch).mockResolvedValue(Response.json({ period: 'all', player: { ...player, rank: -1 } }))
  await expect(getRanking()).rejects.toMatchObject({ code: 'invalid_response' })
  vi.mocked(fetch).mockResolvedValue(Response.json({ period: 'all', content: [player], page: 0, size: 20 }))
  await expect(getLeaderboard('all')).rejects.toMatchObject({ code: 'invalid_response' })
  vi.mocked(fetch).mockResolvedValue(Response.json({ currentStreak: 5, longestStreak: 2, lastCompletionDate: null, timezone: 'UTC' }))
  await expect(getStreak()).rejects.toMatchObject({ code: 'invalid_response' })
})
it('ignores a stale response after selecting a new period', async () => {
  const normal = vi.mocked(fetch).getMockImplementation()!
  let finish: (r: Response) => void = () => {}
  vi.mocked(fetch).mockImplementation((input, init) => String(input).includes('/leaderboard?period=all')
    ? new Promise<Response>(resolve => { finish = resolve }) : normal(input, init))
  const user = userEvent.setup()
  render(<LeaderboardPage />)
  await user.click(screen.getByRole('button', { name: 'Weekly' }))
  expect(await screen.findByText('Weekly player rankings')).toBeVisible()
  finish(Response.json({ period: 'all', content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }))
  await waitFor(() => expect(screen.getByText('Weekly player rankings')).toBeVisible())
})
