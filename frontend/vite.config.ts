import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 7331,
    strictPort: true,
    proxy: {
      // Backend (Spring Boot, ticket #72/#73) runs on 7332; proxy in dev so the
      // session cookie and API calls work without CORS configuration.
      '/api': {
        target: 'http://localhost:7332',
        changeOrigin: true,
      },
    },
  },
})
