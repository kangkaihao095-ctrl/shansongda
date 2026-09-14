import { describe, expect, it } from 'vitest'
import { CAT_KEYWORDS, MERCHANT_COVER, RIDER_COVER, catPlaceholder, imgSrc, isBrokenRemote, localPoolSrc, merchantAvatarSrc, merchantCoverSrc, onImgError, riderAvatarSrc } from '../src/img.js'

describe('imgSrc', () => {
  it('rewrites loremflickr/picsum to local jpeg photos', () => {
    expect(isBrokenRemote('https://loremflickr.com/800/600/pharmacy?lock=1')).toBe(true)
    expect(imgSrc('https://loremflickr.com/800/600/pharmacy?lock=1', 'pharma', 'shop-17', 'shop')).toBe('/images/shops/17.jpg')
    expect(imgSrc('https://picsum.photos/800/600', 'food', 'shop-3', 'shop')).toBe('/images/shops/3.jpg')
    expect(imgSrc('/images/shops/2.jpg', 'food', 'shop-3')).toBe('/images/shops/2.jpg')
    expect(imgSrc('/images/food/shop-2.svg', 'food', 'shop-3', 'shop')).toBe('/images/shops/3.jpg')
    expect(imgSrc('/images/dishes/food/4.jpg', 'food', 'sku-9', 'dish')).toBe('/images/dishes/food/4.jpg')
  })
})

describe('onImgError', () => {
  it('falls back to same-category real jpeg placeholder', () => {
    const el = {
      dataset: { seed: 'ssd-sku-1001', category: 'pharma' },
      src: 'https://loremflickr.com/800/600/pharmacy?lock=1',
      getAttribute(name) {
        return name === 'src' ? this.src : undefined
      }
    }
    onImgError({ target: el })
    expect(el.src).toBe(catPlaceholder('pharma'))
    expect(el.src).toBe('/images/ph/pharma.jpg')
    expect(el.src).not.toContain('loremflickr')
    expect(el.src).not.toContain('.svg')
    expect(CAT_KEYWORDS.pharma).toBe('pharmacy')
    expect(localPoolSrc('food', 'sku-100001', 'dish')).toMatch(/^\/images\/dishes\/food\/\d+\.jpg$/)
  })

  it('honors data-fallback-src for rider avatars', () => {
    const el = {
      dataset: { fallbackSrc: RIDER_COVER },
      src: '/api/avatars/broken.jpg',
      getAttribute(name) {
        return name === 'src' ? this.src : undefined
      }
    }
    onImgError({ target: el })
    expect(el.src).toBe(RIDER_COVER)
  })
})

describe('riderAvatarSrc', () => {
  it('uses the original rider cover instead of empty or picsum', () => {
    expect(riderAvatarSrc('')).toBe(RIDER_COVER)
    expect(riderAvatarSrc('https://picsum.photos/200')).toBe(RIDER_COVER)
    expect(riderAvatarSrc('/api/avatars/2.jpg')).toBe('/api/avatars/2.jpg')
  })
})

describe('merchantCoverSrc', () => {
  it('prefers shop cover / shops/{id}.jpg and falls back to merchant-cover', () => {
    expect(merchantCoverSrc({ coverUrl: '/images/shops/3.jpg', id: 3, category: 'fresh' })).toBe('/images/shops/3.jpg')
    expect(merchantCoverSrc({ id: 3, category: 'fresh' })).toBe('/images/shops/3.jpg')
    expect(merchantCoverSrc({ coverUrl: 'https://picsum.photos/800', id: 17 })).toBe('/images/shops/17.jpg')
    expect(merchantCoverSrc({})).toBe(MERCHANT_COVER)
    expect(merchantAvatarSrc('', { id: 3 })).toBe('/images/shops/3.jpg')
    expect(merchantAvatarSrc('/api/avatars/3.jpg', { id: 3 })).toBe('/api/avatars/3.jpg')
  })
})
