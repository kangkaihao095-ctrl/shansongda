/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.spec.ts']
  },
  server: {
    port: 5175,
    strictPort: true,
    host: '127.0.0.1',
    proxy: {
      '/api': 'http://127.0.0.1:18080'
    }
  }
})
