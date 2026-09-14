export const IMG_PLACEHOLDER = '/images/ph/food.jpg'
export const RIDER_COVER = '/images/rider-cover.jpg'
export const MERCHANT_COVER = '/images/merchant-cover.jpg'

export const CAT_KEYWORDS = {
  food: 'chinese-food',
  dessert: 'bakery',
  market: 'grocery',
  fresh: 'fruit',
  pharma: 'pharmacy',
  flower: 'flowers',
  tea: 'milk-tea',
  errand: 'parcel'
}

export const SHOP_POOL = 15
export const DISH_POOL = 80
const BROKEN_HOST = /loremflickr|picsum|unsplash|lorempixel|placeholder\.com/i
const CATALOG_SVG = /\/images\/(?:food|dessert|market|fresh|pharma|flower|tea|errand)\/(?:shop|dish)-\d+\.svg$/i

export function catPlaceholder(category) {
  const cat = CAT_KEYWORDS[category] ? category : 'food'
  return `/images/ph/${cat}.jpg`
}

export function isBrokenRemote(url) {
  return !url || BROKEN_HOST.test(String(url))
}

function hashSeed(seed) {
  const raw = String(seed || 'ssd')
  let h = 0
  for (let i = 0; i < raw.length; i++) h = (h * 31 + raw.charCodeAt(i)) >>> 0
  return h
}

function poolKind(seed) {
  const raw = String(seed || '')
  if (/sku|dish|seckill|item|food-/i.test(raw)) return 'dish'
  return 'shop'
}

export function localPoolSrc(category, seed, kind) {
  const cat = CAT_KEYWORDS[category] ? category : 'food'
  const k = kind || poolKind(seed)
  const raw = String(seed || '')
  if (k === 'shop') {
    const id = raw.match(/shop-(\d+)/i)?.[1]
    if (id) return `/images/shops/${id}.jpg`
    const n = (hashSeed(seed) % SHOP_POOL) + 1
    return `/images/${cat}/shop-${n}.jpg`
  }
  const n = (hashSeed(seed) % DISH_POOL) + 1
  return `/images/dishes/${cat}/${n}.jpg`
}

/** 外链裂图改写到本站真实 JPG；已是本站照片则原样使用，不改成 SVG。 */
export function imgSrc(url, category, seed, kind) {
  const cat = CAT_KEYWORDS[category] ? category : 'food'
  const s = url == null ? '' : String(url)
  if (s.startsWith('/images/') && !CATALOG_SVG.test(s) && !s.endsWith('.svg')) return s
  if (isBrokenRemote(s) || s.startsWith('data:text') || CATALOG_SVG.test(s) || !s) {
    return localPoolSrc(cat, seed, kind)
  }
  return s
}

export function riderAvatarSrc(url) {
  const s = url == null ? '' : String(url)
  if (!s || isBrokenRemote(s) || s.startsWith('data:text')) return RIDER_COVER
  return s
}

/** 店铺头图：优先 coverUrl / shops/{id}.jpg，否则统一商家封面实拍。 */
export function merchantCoverSrc(merchant) {
  const m = merchant || {}
  const id = m.id || m.userId || m.merchantId
  const cat = m.category || 'fresh'
  if (id || m.coverUrl) return imgSrc(m.coverUrl, cat, id ? 'shop-' + id : 'merchant', 'shop')
  return MERCHANT_COVER
}

export function merchantAvatarSrc(url, merchant) {
  const s = url == null ? '' : String(url)
  if (!s || isBrokenRemote(s) || s.startsWith('data:text')) return merchantCoverSrc(merchant)
  return s
}

/** 失败落到同品类真实照片，不要 SVG 色块。骑手头像可指定 data-fallback-src。 */
export function onImgError(e) {
  const el = e?.target
  if (!el || el.dataset.fallback === 'done') return
  const ph = el.dataset.fallbackSrc || catPlaceholder(el.dataset.category || 'food')
  el.dataset.fallback = 'done'
  const cur = String(el.getAttribute('src') || el.src || '')
  if (cur === ph || cur.endsWith(ph)) return
  el.src = ph
}
