import { BookOpen, Compass, PlusCircle, UserRound, UsersRound, Users, Users } from 'lucide-react'

export const navigation = [
  { to: '/quests', label: 'Explore', icon: Compass, memberOnly: false },
  { to: '/my-quests', label: 'My quests', icon: BookOpen, memberOnly: true },
  { to: '/quests/new', label: 'Create', icon: PlusCircle, memberOnly: true },
  { to: '/profile', label: 'Profile', icon: UserRound, memberOnly: true },
  { to: '/friends', label: 'Friends', icon: UsersRound, memberOnly: true },
  { to: '/teams', label: 'Teams', icon: Users, memberOnly: true },
  { to: '/teams', label: 'Teams', icon: Users, memberOnly: true },
] as const

// member-preview is used exclusively by the development gallery.
export type NavigationMode = 'guest' | 'authenticated' | 'member-preview'
