import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.FIRSTHOME_DEV_API_ORIGIN || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
