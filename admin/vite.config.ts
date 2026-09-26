import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 管理后台 dev server：5174，/api 代理到后端 8080（避开跨端口 CORS）
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
