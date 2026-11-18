import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })
// jsdom has no scrolling layout. Browser tests verify navigation and focus.
window.scrollTo = vi.fn()
