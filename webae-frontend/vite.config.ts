import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

// The build helper stages Vite output in .workspace and promotes it only after
// a successful build. `base: './'` keeps all asset URLs relative because
// NanoHTTPD serves from the root path. Keeping emptyOutDir false here also
// makes an accidental direct Vite invocation non-destructive; the helper
// overrides it to true for its fresh staging directory.
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
