import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { createAppRoutes } from './router'
import { AuthProvider } from '../features/auth/AuthContext'

const router = createBrowserRouter(createAppRoutes())
export default function App() {
  return <AuthProvider><RouterProvider router={router} /></AuthProvider>
}
