import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': backendUrl,
      '/oauth2': { target: backendUrl, changeOrigin: true },
      '/login': backendUrl,
      '/transactions': backendUrl,
      '/accounts': backendUrl,
      '/categories': backendUrl,
      '/users': backendUrl,
      '/thresholds': backendUrl,
    },
  },
})
