import { describe, expect, it } from 'vitest'
import { authReturnPath, safeReturnPath } from './safeReturnPath'

describe('safeReturnPath', () => {
  it.each([
    ['/my-quests?tab=drafts#recent', '/my-quests?tab=drafts#recent'],
    ['https://evil.example/path', '/quests'],
    ['//evil.example/path', '/quests'],
    ['/login', '/quests'],
    ['/register', '/quests'],
    ['/\\evil.example', '/quests'],
    ['quests', '/quests'],
    [undefined, '/quests'],
  ])('normalizes %s to %s', (value, expected) => {
    expect(safeReturnPath(value, 'https://sidequest.test')).toBe(expected)
  })

  it('uses a protected route location before query parameters', () => {
    expect(authReturnPath({ from: { pathname: '/profile', search: '?tab=badges', hash: '#earned' } }, '?returnTo=/quests'))
      .toBe('/profile?tab=badges#earned')
  })

  it('accepts a safe returnTo query parameter', () => {
    expect(authReturnPath(null, '?returnTo=%2Fquests%2F123')).toBe('/quests/123')
  })
})
