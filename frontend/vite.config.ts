import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_BACKEND_URL || 'http://localhost:8080';

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/documents': {
          target,
          changeOrigin: true,
        },
        '/rag': {
          target,
          changeOrigin: true,
        },
        '/auth': {
          target,
          changeOrigin: true,
        },
        '/admin': {
          target,
          changeOrigin: true,
        },
        '/api': {
          target,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  };
});