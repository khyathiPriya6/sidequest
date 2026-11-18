import { Navigate, type RouteObject } from 'react-router-dom'
import { AppShell } from './layouts/AppShell'
import { ExploreShell } from '../pages/ExploreShell'
import { FeaturePlaceholder } from '../pages/FeaturePlaceholder'
import { NotFoundPage } from '../pages/NotFoundPage'

const placeholders = [
  { path: '/quests/new', title: 'Create a quest', description: 'Quest creation will be available after sign-in is ready.' },
  { path: '/quests/:id/edit', title: 'Edit a quest', description: 'Editing your own quest drafts will be available soon.' },
  { path: '/quests/:id', title: 'Quest details', description: 'Quest details will be available when the catalogue opens.' },
  { path: '/my-quests', title: 'My quests', description: 'Your quest log will be available after sign-in is ready.' },
  { path: '/profile', title: 'Your profile', description: 'Your profile and earned progress will be available after sign-in is ready.' },
  { path: '/login', title: 'Sign in', description: 'Sign-in is not available yet. You can return to Explore while we get things ready.' },
  { path: '/register', title: 'Create an account', description: 'Registration is not available yet. Check back soon.' },
]

export function createAppRoutes(): RouteObject[] {
  return [
    {
      element: <AppShell />,
      children: [
        { path: '/', element: <Navigate to="/quests" replace /> },
        { path: '/quests', element: <ExploreShell />, handle: { title: 'Explore' } },
        ...placeholders.map(({ path, title, description }) => ({ path, handle: { title }, element: <FeaturePlaceholder title={title} description={description} /> })),
        { path: '*', element: <NotFoundPage />, handle: { title: 'Page not found' } },
      ],
    },
    ...(import.meta.env.DEV ? [{ path: '/dev/components', handle: { title: 'Component preview' },
      lazy: async () => ({ Component: (await import('../dev/ComponentPreview')).ComponentPreview }),
    }] : []),
  ]
}
