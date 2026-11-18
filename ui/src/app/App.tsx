import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { createAppRoutes } from './router'

const router = createBrowserRouter(createAppRoutes())
export default function App() { return <RouterProvider router={router} /> }
