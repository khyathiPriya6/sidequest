import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'API_')
  const proxy = {
    '/api': {
      target: process.env.API_ORIGIN ?? env.API_ORIGIN ?? 'http://localhost:8080',
      changeOrigin: true,
    },
  }
  return {
    plugins: [react()],
    server: { proxy },
    preview: { proxy },
  }
})
