import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/transactions': 'http://localhost:8080',
      '/accounts': 'http://localhost:8080',
      '/categories': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
      '/thresholds': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/login': 'http://localhost:8080',
    },
  },
})
