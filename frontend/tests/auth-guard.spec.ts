import { describe, expect, it } from 'vitest'
import { resolveNavigation } from '../src/guard.js'

describe('resolveNavigation', () => {
  it('sends anonymous users to login', () => {
    expect(resolveNavigation({ path: '/home', meta: { role: 'USER' } }, null)).toBe('/login')
    expect(resolveNavigation({ path: '/login', meta: { public: true } }, null)).toBe(true)
  })

  it('sends logged-in users away from login', () => {
    expect(resolveNavigation({ path: '/login', meta: { public: true } }, { role: 'RIDER' })).toBe('/rider')
    expect(resolveNavigation({ path: '/login', meta: { public: true } }, { role: 'MERCHANT' })).toBe('/shop')
  })

  it('blocks cross-role homes', () => {
    expect(resolveNavigation({ path: '/home', meta: { role: 'USER' } }, { role: 'RIDER' })).toBe('/rider')
    expect(resolveNavigation({ path: '/coupons', meta: { role: 'USER' } }, { role: 'MERCHANT' })).toBe('/shop')
    expect(resolveNavigation({ path: '/orders', meta: {} }, { role: 'USER' })).toBe(true)
    expect(resolveNavigation({ path: '/rider/income', meta: { role: 'RIDER' } }, { role: 'RIDER' })).toBe(true)
    expect(resolveNavigation({ path: '/rider/income', meta: { role: 'RIDER' } }, { role: 'USER' })).toBe('/home')
    expect(resolveNavigation({ path: '/me/addresses', meta: { role: 'USER' } }, { role: 'USER' })).toBe(true)
    expect(resolveNavigation({ path: '/me/addresses', meta: { role: 'USER' } }, { role: 'RIDER' })).toBe('/rider')
    expect(resolveNavigation({ path: '/shop', meta: { role: 'MERCHANT' } }, { role: 'USER' })).toBe('/home')
  })
})
