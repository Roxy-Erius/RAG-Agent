import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // SSE 走同一个 /api，这里必须允许 WebSocket/SSE 长连接
        ws: false,
        // SSE 需要这条：不缓冲，直接转发
        secure: false,
      },
    },
  },
  build: {
    target: 'es2020',
    outDir: 'dist',
    sourcemap: true,
  },
});
