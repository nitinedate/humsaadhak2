import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';

// In development the Spring Boot app runs on :8080; Vite proxies API calls so cookies stay same-origin.
// `--mode artifact` produces one self-contained HTML file of the demo for sharing.
export default defineConfig(({ mode }) => ({
  plugins: mode === 'artifact' ? [react(), viteSingleFile()] : [react()],
  // Escape every non-ASCII character in the JavaScript output (e.g. U+FFFD inside the Excel/PDF libraries), so bundles
  // are plain ASCII and survive any proxy, CDN or viewer that re-encodes text. Runtime behaviour is unchanged.
  esbuild: { charset: 'ascii' },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' },
  },
  build: {
    outDir: mode === 'artifact' ? 'dist-preview' : 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 700,
  },
}));
