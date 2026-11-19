import { BookOpen, Compass, PlusCircle, UserRound } from 'lucide-react'

export const navigation = [
  { to: '/quests', label: 'Explore', icon: Compass, memberOnly: false },
  { to: '/my-quests', label: 'My quests', icon: BookOpen, memberOnly: true },
  { to: '/quests/new', label: 'Create', icon: PlusCircle, memberOnly: true },
  { to: '/profile', label: 'Profile', icon: UserRound, memberOnly: true },
] as const

// member-preview is used exclusively by the development gallery.
export type NavigationMode = 'guest' | 'authenticated' | 'member-preview'
