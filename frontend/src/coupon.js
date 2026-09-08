export function couponIcon(c) {
  const key = (c && (c.icon || 'minus')) || 'minus'
  return c?.iconUrl || `/images/coupons/${key}.svg`
}

export function couponTone(c) {
  if (c?.memberOnly || c?.icon === 'member' || c?.scene === 'MEMBER') return 'gold'
  return 'red'
}

export function couponAmount(c) {
  if (!c) return '0'
  if (c.type === 'PERCENT') return `${c.percentOff || 0}折`
  const yuan = ((Number(c.discountCents) || 0) / 100)
  return Number.isInteger(yuan) ? String(yuan) : yuan.toFixed(1)
}

/** 封面大字：¥15 / 8折，不带券 id。 */
export function couponFaceLabel(c) {
  if (!c) return '¥0'
  if (c.type === 'PERCENT') return couponAmount(c)
  return `¥${couponAmount(c)}`
}

export function couponThreshold(c) {
  const min = Number(c?.minSpendCents) || 0
  if (min <= 0) return '无门槛'
  return `满 ¥${(min / 100).toFixed(0)} 可用`
}

export function couponNo(c) {
  if (!c) return ''
  return c.couponId || c.id || c.code || ''
}

export function expireText(endAt) {
  if (!endAt) return ''
  const d = new Date(endAt)
  if (Number.isNaN(d.getTime())) return ''
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi} 到期`
}

export function couponRules(c) {
  const lines = [couponThreshold(c) + '，按下单页结算为准']
  if (c?.memberOnly) lines.push('仅闪会员可用')
  if (c?.type === 'PERCENT') lines.push(`商品合计按 ${c.percentOff || 0} 折，运费不参与折扣`)
  else lines.push('抵扣商品金额，不抵运费')
  lines.push('不可与部分活动同享；过期自动失效')
  return lines
}

export function shanghaiDate() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
}

export const LOGIN_GIFT_SHOWN_KEY = 'ssd.loginGiftShown'

export function loginGiftStorageKey(userId, date) {
  return `ssd.loginGift:${userId}:${date || shanghaiDate()}`
}

export function loginGiftDismissKey(userId, date) {
  return `ssd.loginGiftDismissed:${userId}:${date || shanghaiDate()}`
}

export function markLoginGiftClaimed(userId, date) {
  try { localStorage.setItem(loginGiftStorageKey(userId, date), '1') } catch { /* ignore */ }
}

export function loginGiftClaimedLocal(userId, date) {
  try { return localStorage.getItem(loginGiftStorageKey(userId, date)) === '1' } catch { return false }
}

export function markLoginGiftDismissed(userId, date) {
  if (userId == null) return
  try { localStorage.setItem(loginGiftDismissKey(userId, date), '1') } catch { /* ignore */ }
}

export function loginGiftDismissedLocal(userId, date) {
  try { return localStorage.getItem(loginGiftDismissKey(userId, date)) === '1' } catch { return false }
}

export function loginGiftShownThisSession(userId, date) {
  try { return sessionStorage.getItem(LOGIN_GIFT_SHOWN_KEY) === `${userId}:${date || shanghaiDate()}` } catch { return false }
}

export function markLoginGiftShown(userId, date) {
  if (userId == null) return
  try { sessionStorage.setItem(LOGIN_GIFT_SHOWN_KEY, `${userId}:${date || shanghaiDate()}`) } catch { /* ignore */ }
}

export function clearLoginGiftShown() {
  try { sessionStorage.removeItem(LOGIN_GIFT_SHOWN_KEY) } catch { /* ignore */ }
}

/** 当天已领 / 已关掉 / 本会话已弹过 → 不再自动弹。 */
export function shouldSkipLoginGiftPopup(userId, date) {
  if (userId == null) return true
  const d = date || shanghaiDate()
  if (loginGiftShownThisSession(userId, d)) return true
  if (loginGiftClaimedLocal(userId, d)) return true
  if (loginGiftDismissedLocal(userId, d)) return true
  return false
}

export function loginGiftEligible(gift) {
  if (!gift) return false
  if (gift.alreadyClaimed || gift.claimed) return false
  if (gift.eligible === false) return false
  return Array.isArray(gift.items) && gift.items.length > 0
}

export const LANDMARKS = [
  { key: 'waitan', name: '外滩', lat: 31.2397, lon: 121.4903, detail: '上海市黄浦区外滩源 33 号' },
  { key: 'xujiahui', name: '徐家汇', lat: 31.1946, lon: 121.4367, detail: '上海市徐汇区徐家汇港汇广场' },
  { key: 'wujiaochang', name: '五角场', lat: 31.2994, lon: 121.5145, detail: '上海市杨浦区五角场创智天地' },
  { key: 'lujiazui', name: '浦东陆家嘴', lat: 31.2354, lon: 121.5056, detail: '上海市浦东新区陆家嘴国金中心' }
]
