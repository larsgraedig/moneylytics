import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/auth': backendUrl,
      '/oauth2': { target: backendUrl, changeOrigin: true },
      '/transactions': backendUrl,
      '/accounts': backendUrl,
      '/categories': backendUrl,
      '/users': backendUrl,
      '/thresholds': backendUrl,
      '/budgets': backendUrl,
      '/collections': backendUrl,
      '/admin': backendUrl,
      '/organizations': backendUrl,
      '/invitations': backendUrl,
      '/subscriptions': backendUrl,
      '/imports': backendUrl,
      '/invoices': backendUrl,
      '/webhooks': backendUrl,
    },
  },
})
