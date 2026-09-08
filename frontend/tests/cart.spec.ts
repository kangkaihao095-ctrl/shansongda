import { describe, expect, it } from 'vitest'
import { addSku, cart, clearCart, goodsCents, setShop } from '../src/cart.js'

describe('cart', () => {
  it('adds the same sku as quantity', () => {
    setShop({ id: 3, shopName: '鲜生', coverUrl: '/a.jpg' })
    addSku({ id: 1001, name: '拼盘', priceCents: 1990, imageUrl: '/f.jpg' }, 1)
    addSku({ id: 1001, name: '拼盘', priceCents: 1990 }, 2)
    expect(cart.items).toHaveLength(1)
    expect(cart.items[0].qty).toBe(3)
    expect(goodsCents()).toBe(5970)
  })

  it('clears items when switching shop', () => {
    setShop({ id: 3, shopName: '鲜生' })
    addSku({ id: 1, name: 'A', priceCents: 100 })
    setShop({ id: 4, shopName: '另一家' })
    expect(cart.items).toEqual([])
    expect(cart.merchantId).toBe(4)
  })

  it('persists to localStorage', () => {
    setShop({ id: 3, shopName: '鲜生', coverUrl: '/c.jpg' })
    addSku({ id: 2, name: 'B', priceCents: 200 })
    const saved = JSON.parse(localStorage.getItem('ssd.cart') || 'null')
    expect(saved.merchantId).toBe(3)
    expect(saved.items[0].qty).toBe(1)
    clearCart()
    expect(JSON.parse(localStorage.getItem('ssd.cart') || 'null').items).toEqual([])
  })
})
