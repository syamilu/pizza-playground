import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    // jsdom only exposes window.localStorage when the document has a real
    // origin, so give it one. Without this, any test touching localStorage
    // (the cart) sees `window.localStorage === undefined`.
    environmentOptions: {
      jsdom: { url: 'http://localhost:3000' },
    },
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Co-located tests under src/ plus the dedicated suite under tests/.
    // Pure-logic specs in tests/unit opt into the node environment via a
    // `// @vitest-environment node` docblock; component specs use jsdom (default).
    include: [
      'src/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}',
      'tests/unit/**/*.{test,spec}.{ts,tsx}',
      'tests/component/**/*.{test,spec}.{ts,tsx}',
    ],
    // tests/e2e is Playwright territory — keep Vitest out of it.
    exclude: ['node_modules', '.next', 'cypress', 'tests/e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: ['node_modules', '.next', 'cypress']
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
