import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it, vi } from 'vitest'
import { QuestCard } from './QuestCard'

it('labels earnable rewards and links to details without fetching or mutating anything', () => {
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  render(<MemoryRouter><QuestCard title="A long creative adventure" description="Explore a new idea" category="Creativity"
    difficulty="Easy" rewardXp={50} href="/quests/example" /></MemoryRouter>)
  expect(screen.getByRole('article', { name: 'A long creative adventure' })).toBeVisible()
  expect(screen.getByRole('link', { name: 'A long creative adventure' })).toHaveAttribute('href', '/quests/example')
  expect(screen.getByText('50 XP reward')).toBeVisible()
  expect(screen.queryByText(/earned/i)).not.toBeInTheDocument()
  expect(fetch).not.toHaveBeenCalled()
})

it('handles missing content and optional artwork with meaningful alternatives', () => {
  render(<MemoryRouter><QuestCard title="" description="" category="" difficulty="Hard" rewardXp={NaN}
    href="/quests/example" artwork={{ src: '/example.png', alt: 'A woodland trail' }} /></MemoryRouter>)
  expect(screen.getByRole('link', { name: 'Untitled quest' })).toBeVisible()
  expect(screen.getByText('Reward unavailable')).toBeVisible()
  expect(screen.getByText('No description available.')).toBeVisible()
  expect(screen.getByRole('img', { name: 'A woodland trail' })).toHaveAttribute('src', '/example.png')
})
