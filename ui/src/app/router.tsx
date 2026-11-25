import { Navigate, useLocation, useParams, type RouteObject } from 'react-router-dom'
import { AppShell } from './layouts/AppShell'
import { ExploreShell } from '../pages/ExploreShell'
import { QuestDetailPage, ParticipationDetailPage } from '../pages/QuestDetailPage'
import { LeaderboardPage } from '../features/progress/LeaderboardPage'
import { PlayerProfilePage } from '../features/progress/PlayerProfilePage'
import { ModeratorDashboard } from '../features/safety/ModeratorDashboard'
import { NotFoundPage } from '../pages/NotFoundPage'
import { AuthPage } from '../features/auth/AuthPage'
import { RequireAuth } from '../features/auth/RequireAuth'
import { MyQuestsPage } from '../pages/MyQuestsPage'
import { QuestDraftPage } from '../pages/QuestDraftPage'

function LegacyQuestDashboardRedirect() {
  const location = useLocation()
  return <Navigate to={`/my-quests${location.search}${location.hash}`} replace />
}

function LegacyQuestDashboardEditRedirect() {
  const { id = '' } = useParams()
  return <Navigate to={`/quests/${encodeURIComponent(id)}/edit`} replace />
}

export function createAppRoutes(): RouteObject[] {
  return [
    {
      element: <AppShell />,
      children: [
        { path: '/', element: <Navigate to="/quests" replace /> },
        { path: '/quests', element: <ExploreShell />, handle: { title: 'Explore' } },
        { path: '/quests/:id', element: <QuestDetailPage />, handle: { title: 'Quest details' } },
        { path: '/my/quests', element: <LegacyQuestDashboardRedirect /> },
        { path: '/login', element: <AuthPage mode="login" />, handle: { title: 'Sign in' } },
        { path: '/register', element: <AuthPage mode="register" />, handle: { title: 'Create an account' } },
        {
          element: <RequireAuth />,
          children: [
            { path: '/quests/new', handle: { title: 'Create a quest' }, element: <QuestDraftPage mode="create" /> },
            { path: '/my/quests/new', element: <Navigate to="/quests/new" replace /> },
            { path: '/quests/:id/edit', handle: { title: 'Edit a quest' }, element: <QuestDraftPage mode="edit" /> },
            { path: '/my/quests/:id/edit', element: <LegacyQuestDashboardEditRedirect /> },
            { path: '/my-quests', handle: { title: 'My quests' }, element: <MyQuestsPage /> },
            { path: '/moderation', handle: { title: 'Quest moderation' }, element: <ModeratorDashboard /> },
            { path: '/leaderboard', handle: { title: 'Leaderboard' }, element: <LeaderboardPage /> },
            { path: '/profile', handle: { title: 'Your profile' }, element: <PlayerProfilePage /> },
            { path: '/participations/:id', handle: { title: 'Participation details' }, element: <ParticipationDetailPage /> },
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
