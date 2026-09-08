import { afterEach, vi } from 'vitest'
import { clearCart, cart } from '../src/cart.js'

afterEach(() => {
  localStorage.clear()
  clearCart()
  cart.merchantId = null
  cart.shopName = ''
  cart.coverUrl = ''
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false
    })
  })
}
