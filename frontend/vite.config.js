import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server forwards REST and WebSocket traffic to the Spring Boot backend,
// so the browser sees one origin and no CORS configuration is needed.
const BACKEND = process.env.BACKEND_URL || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': BACKEND,
      '/ws': { target: BACKEND, ws: true },
    },
  },
});
