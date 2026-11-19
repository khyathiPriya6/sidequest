import { Navigate, useLocation, type RouteObject } from 'react-router-dom'
import { AppShell } from './layouts/AppShell'
import { ExploreShell } from '../pages/ExploreShell'
import { QuestDetailPage } from '../pages/QuestDetailPage'
import { FeaturePlaceholder } from '../pages/FeaturePlaceholder'
import { NotFoundPage } from '../pages/NotFoundPage'
import { AuthPage } from '../features/auth/AuthPage'
import { RequireAuth } from '../features/auth/RequireAuth'

const placeholders = [
  { path: '/quests/:id', title: 'Quest details', description: 'Quest details will be available when the catalogue opens.' },
]

export function createAppRoutes(): RouteObject[] {
  return [
    {
      element: <AppShell />,
      children: [
        { path: '/', element: <Navigate to="/quests" replace /> },
        { path: '/quests', element: <ExploreShell />, handle: { title: 'Explore' } },
        ...placeholders.map(({ path, title, description }) => ({ path, handle: { title }, element: <FeaturePlaceholder title={title} description={description} /> })),
        { path: '/login', element: <AuthPage mode="login" />, handle: { title: 'Sign in' } },
        { path: '/register', element: <AuthPage mode="register" />, handle: { title: 'Create an account' } },
        {
          element: <RequireAuth />,
          children: [
            { path: '/quests/new', handle: { title: 'Create a quest' }, element: <FeaturePlaceholder title="Create a quest" description="Quest creation will be available soon." /> },
            { path: '/quests/:id/edit', handle: { title: 'Edit a quest' }, element: <FeaturePlaceholder title="Edit a quest" description="Editing your own quest drafts will be available soon." /> },
            { path: '/my-quests', handle: { title: 'My quests' }, element: <FeaturePlaceholder title="My quests" description="Your quest log will be available soon." /> },
            { path: '/profile', handle: { title: 'Your profile' }, element: <FeaturePlaceholder title="Your profile" description="Your profile and earned progress will be available soon." /> },
          ],
        },
        { path: '*', element: <NotFoundPage />, handle: { title: 'Page not found' } },
      ],
    },
    ...(import.meta.env.DEV ? [{ path: '/dev/components', handle: { title: 'Component preview' },
      lazy: async () => ({ Component: (await import('../dev/ComponentPreview')).ComponentPreview }),
    }] : []),
  ]
}
