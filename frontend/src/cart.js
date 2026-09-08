import { reactive } from 'vue'

const KEY = 'ssd.cart'

export const cart = reactive({
  merchantId: null,
  shopName: '',
  coverUrl: '',
  items: []
})

hydrate()

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
