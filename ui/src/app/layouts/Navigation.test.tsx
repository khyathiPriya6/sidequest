import { expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Navigation } from './Navigation'

function renderNavigation(mode: 'guest' | 'authenticated', isModerator: boolean, mobile = false) {
  return render(<MemoryRouter><Navigation mode={mode} isModerator={isModerator} mobile={mobile} /></MemoryRouter>)
}

it('does not reveal moderation link to guests', () => {
  renderNavigation('guest', false)
  expect(screen.queryByRole('link', { name: 'Moderation' })).toBeNull()
})

it('does not reveal moderation link to regular members', () => {
  renderNavigation('authenticated', false)
  expect(screen.queryByRole('link', { name: 'Moderation' })).toBeNull()
  expect(screen.getByRole('link', { name: 'Teams' })).toBeTruthy()
})

it('displays moderation link for verified moderators in desktop and mobile navigation', () => {
  const { unmount } = renderNavigation('authenticated', true)
  expect(screen.getByRole('link', { name: 'Moderation' }).getAttribute('href')).toBe('/moderation')
  unmount()
  renderNavigation('authenticated', true, true)
  expect(screen.getByRole('link', { name: 'Moderation' }).getAttribute('href')).toBe('/moderation')
})
