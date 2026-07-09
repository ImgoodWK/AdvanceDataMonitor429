import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

// Build output goes directly into the mod's static resources directory so the
// embedded NanoHTTPD server serves the compiled bundle. `base: './'` keeps all
// asset URLs relative because NanoHTTPD serves from the root path.
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: resolve(__dirname, '../src/main/resources/assets/textech/webae'),
    emptyOutDir: false,
    assetsDir: 'assets',
    cssCodeSplit: true,
    sourcemap: false,
    // Keep chunk sizes reasonable; antd is large so we let Vite split vendors.
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // Stable file names with content hash for cache busting.
        entryFileNames: 'js/[name]-[hash].js',
        chunkFileNames: 'js/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
        manualChunks: {
          react: ['react', 'react-dom'],
          antd: ['antd', '@ant-design/icons'],
          gridstack: ['gridstack'],
        },
      },
    },
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'node',
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true,
      },
      '/icons': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true,
      },
    },
  },
});
