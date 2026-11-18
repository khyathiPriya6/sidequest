import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { XPProgressBar } from './XPProgressBar'

describe('earned XP presentation', () => {
  it('reports earned progress and bounds the bar without inventing a level', () => {
    render(<XPProgressBar earned={350} target={300} label="Earned progress" />)
    expect(screen.getByRole('progressbar', { name: 'Earned progress' })).toHaveAttribute('value', '300')
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuetext', '350 XP earned toward a 300 XP target')
    expect(screen.getByText('350 / 300 XP earned')).toBeVisible()
  })
  it('represents zero earned XP as zero progress', () => {
    render(<XPProgressBar earned={0} target={100} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('value', '0')
  })
  it.each([[10, 0], [10, -1], [-1, 100], [Number.NaN, 100], [10, Infinity]])(
    'handles invalid earned/target values %s/%s without a misleading progress bar', (earned, target) => {
      render(<XPProgressBar earned={earned} target={target} />)
      expect(screen.getByText('Progress unavailable')).toBeVisible()
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    },
  )
})
