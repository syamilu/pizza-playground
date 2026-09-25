import '@testing-library/jest-dom'

// jsdom in this toolchain (jsdom 27 + Vitest 4) does not always expose
// window.localStorage, which the cart relies on. Install a minimal in-memory
// Storage shim when it is missing so cart tests behave deterministically.
if (typeof window !== 'undefined' && !window.localStorage) {
  const store = new Map<string, string>()
  const localStorageShim: Storage = {
    get length() {
      return store.size
    },
    clear: () => store.clear(),
    getItem: (key: string) => (store.has(key) ? store.get(key)! : null),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    removeItem: (key: string) => {
      store.delete(key)
    },
    setItem: (key: string, value: string) => {
      store.set(key, String(value))
    },
  }
  Object.defineProperty(window, 'localStorage', {
    value: localStorageShim,
    configurable: true,
  })
}
