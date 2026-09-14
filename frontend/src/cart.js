import { reactive } from 'vue'
import { api } from './api'

const KEY = 'ssd.cart'

export const cart = reactive({
  merchantId: null,
  shopName: '',
  coverUrl: '',
  items: []
})

hydrate()
let syncTimer

function hydrate() {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    cart.merchantId = data.merchantId ?? null
    cart.shopName = data.shopName || ''
    cart.coverUrl = data.coverUrl || ''
    cart.items = Array.isArray(data.items) ? data.items : []
  } catch {
    // 损坏的本地购物车忽略
  }
}

function persist() {
  localStorage.setItem(KEY, JSON.stringify({
    merchantId: cart.merchantId,
    shopName: cart.shopName,
    coverUrl: cart.coverUrl,
    items: cart.items
  }))
  clearTimeout(syncTimer)
  syncTimer = setTimeout(() => { syncCartToServer() }, 800)
}

export async function syncCartToServer() {
  try {
    if (!localStorage.getItem('ssd.token')) return
    await api('/api/me/cart', {
      method: 'PUT',
      body: {
        merchantId: cart.merchantId,
        shopName: cart.shopName,
        coverUrl: cart.coverUrl,
        items: cart.items
      }
    })
  } catch {
    /* 未登录或草稿失败不挡加购 */
  }
}

export async function mergeServerCart() {
  let server
  try {
    server = (await api('/api/me/cart')).data
  } catch {
    return
  }
  const serverItems = Array.isArray(server?.items) ? server.items : []
  if (!cart.items.length && serverItems.length) {
    cart.merchantId = server.merchantId ?? null
    cart.shopName = server.shopName || ''
    cart.coverUrl = server.coverUrl || ''
    cart.items = serverItems
    localStorage.setItem(KEY, JSON.stringify({
      merchantId: cart.merchantId,
      shopName: cart.shopName,
      coverUrl: cart.coverUrl,
      items: cart.items
    }))
    return
  }
  if (cart.items.length && serverItems.length && cart.merchantId === server.merchantId) {
    const map = new Map()
    for (const it of serverItems) map.set(it.skuId, { ...it })
    for (const it of cart.items) {
      const hit = map.get(it.skuId)
      if (!hit) map.set(it.skuId, { ...it })
      else hit.qty = Math.max(Number(hit.qty) || 1, Number(it.qty) || 1)
    }
    cart.items = [...map.values()]
    persist()
    return
  }
  if (cart.items.length) persist()
}

export function fillFromSnapshot(merchantId, shopName, coverUrl, items) {
  cart.merchantId = merchantId
  cart.shopName = shopName || ''
  cart.coverUrl = coverUrl || ''
  cart.items = (items || []).map((it) => ({
    skuId: it.skuId,
    name: it.name,
    priceCents: it.priceCents,
    imageUrl: it.imageUrl,
    spec: it.spec,
    qty: it.qty || 1
  }))
  persist()
}

export function setShop(merchant) {
  if (cart.merchantId && cart.merchantId !== merchant.id && cart.items.length) {
    cart.items = []
  }
  cart.merchantId = merchant.id
  cart.shopName = merchant.shopName
  cart.coverUrl = merchant.coverUrl || ''
  persist()
}

export function addSku(sku, qty = 1) {
  const hit = cart.items.find((it) => it.skuId === sku.id)
  if (hit) {
    hit.qty += qty
    persist()
    return
  }
  cart.items.push({
    skuId: sku.id,
    name: sku.name,
    priceCents: sku.priceCents,
    imageUrl: sku.imageUrl,
    spec: sku.spec,
    qty
  })
  persist()
}

export function setQty(skuId, qty) {
  const hit = cart.items.find((it) => it.skuId === skuId)
  if (!hit) return
  if (qty <= 0) {
    cart.items = cart.items.filter((it) => it.skuId !== skuId)
    persist()
    return
  }
  hit.qty = qty
  persist()
}

export function goodsCents() {
  return cart.items.reduce((sum, it) => sum + it.priceCents * it.qty, 0)
}

export function itemCount() {
  return cart.items.reduce((sum, it) => sum + it.qty, 0)
}

export function clearCart() {
  cart.items = []
  persist()
}
