import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/transactions': 'http://localhost:8080',
      '/accounts': 'http://localhost:8080',
      '/categories': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
      '/thresholds': 'http://localhost:8080',
      '/budgets': 'http://localhost:8080',
      '/collections': 'http://localhost:8080',
    },
  },
})
