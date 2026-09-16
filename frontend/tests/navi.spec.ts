import { afterEach, describe, expect, it, vi } from 'vitest'
import { openNavi } from '../src/navi.js'

describe('openNavi', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('does nothing without coordinates', () => {
    const href = { value: '' }
    Object.defineProperty(window, 'location', { configurable: true, value: { set href(v: string) { href.value = v }, get href() { return href.value } } })
    const open = vi.fn()
    vi.stubGlobal('open', open)
    openNavi(null, 121.47, '外滩')
    expect(href.value).toBe('')
    expect(open).not.toHaveBeenCalled()
  })

  it('opens iOS amap scheme then Apple Maps fallback', () => {
    vi.useFakeTimers()
    const href = { value: '' }
    Object.defineProperty(window, 'location', { configurable: true, value: { set href(v: string) { href.value = v }, get href() { return href.value } } })
    Object.defineProperty(window.navigator, 'userAgent', { configurable: true, value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)' })
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    const open = vi.fn()
    vi.stubGlobal('open', open)
    openNavi(31.2397, 121.4903, '外滩')
    expect(href.value).toContain('iosamap://navi')
    expect(href.value).toContain('lat=31.2397')
    vi.advanceTimersByTime(800)
    expect(open).toHaveBeenCalledWith(expect.stringContaining('maps.apple.com'), '_blank')
  })

  it('opens Android amapuri then web navigation fallback', () => {
    vi.useFakeTimers()
    const href = { value: '' }
    Object.defineProperty(window, 'location', { configurable: true, value: { set href(v: string) { href.value = v }, get href() { return href.value } } })
    Object.defineProperty(window.navigator, 'userAgent', { configurable: true, value: 'Mozilla/5.0 (Linux; Android 14)' })
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    const open = vi.fn()
    vi.stubGlobal('open', open)
    openNavi(31.2397, 121.4903, '外滩')
    expect(href.value).toContain('amapuri://route/plan/')
    expect(href.value).toContain('dlat=31.2397')
    vi.advanceTimersByTime(800)
    expect(open).toHaveBeenCalledWith(expect.stringContaining('uri.amap.com/navigation'), '_blank')
  })
})
