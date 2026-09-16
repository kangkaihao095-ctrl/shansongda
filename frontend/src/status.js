export const ORDER_STATUS_LABEL = {
  CREATED: '待支付',
  MERCHANT_PENDING: '待商家接单',
  PAID: '商家已接单·备餐中',
  ACCEPTED: '骑手已接单',
  ARRIVED: '骑手到店',
  DELIVERING: '配送中',
  COMPLETED: '已完成',
  CANCELLING: '取消中',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '退款成功',
  REFUND_REJECTED: '退款拒绝'
}

export const ONLINE_STATUS_LABEL = {
  ONLINE: '在线',
  OFFLINE: '离线'
}

export const SHOP_STATUS_LABEL = {
  ONLINE: '营业中',
  OFFLINE: '休息中'
}

export const ACCEPT_STATUS_LABEL = {
  IDLE: '空闲',
  BUSY: '配送中'
}

export const PAY_CHANNEL_LABEL = {
  WECHAT: '微信支付',
  ALIPAY: '支付宝'
}

export const CANCEL_REASONS = [
  { code: 'SLOW', label: '出餐慢' },
  { code: 'ADDRESS', label: '地址错误' },
  { code: 'WAIT', label: '不想等了' },
  { code: 'DUP', label: '重复下单' },
  { code: 'OTHER', label: '其他' }
]

export const REFUND_REASONS = [
  { code: 'QUALITY', label: '商品质量' },
  { code: 'RIDER', label: '骑手原因' },
  { code: 'CHANGE', label: '不想要了' },
  { code: 'STOCK', label: '商家缺货' },
  { code: 'OTHER', label: '其他' }
]

export const TIP_GIFTS = [
  { code: 'WATER', cents: 200, label: '送瓶水', icon: '/images/tips/water.svg' },
  { code: 'MILKTEA', cents: 500, label: '请喝奶茶', icon: '/images/tips/milktea.svg' },
  { code: 'GIFT', cents: 1000, label: '送份小礼物', icon: '/images/tips/gift.svg' },
  { code: 'CHICKEN', cents: 2000, label: '加个鸡腿', icon: '/images/tips/chicken.svg' }
]

export function orderStatusText(status) {
  return ORDER_STATUS_LABEL[status] || status || ''
}

export function onlineStatusText(status) {
  return ONLINE_STATUS_LABEL[status] || status || '离线'
}

export function shopStatusText(status) {
  return SHOP_STATUS_LABEL[status] || status || '营业中'
}

export function acceptStatusText(status) {
  return ACCEPT_STATUS_LABEL[status] || status || '空闲'
}

export function payChannelText(channel) {
  return PAY_CHANNEL_LABEL[channel] || '未支付'
}

export function etaFallback(status) {
  if (status === 'PAID' || status === 'MERCHANT_PENDING' || status === 'CREATED') return '商家备餐中'
  return '骑手正在赶往商家'
}

export function etaClock(etaMs, now = Date.now()) {
  const at = new Date(now + Number(etaMs))
  const pad = (x) => String(x).padStart(2, '0')
  return `${pad(at.getHours())}:${pad(at.getMinutes())}`
}

export function etaLabel(etaMs, status) {
  if (etaMs == null || etaMs === '' || Number(etaMs) <= 0) return etaFallback(status)
  return `预计 ${etaClock(etaMs)} 送达`
}

export function orderStatusHeadline(status, etaMs, role) {
  if (status === 'DELIVERING') {
    const eta = etaLabel(etaMs, status)
    return eta.startsWith('预计') ? `配送中（${eta}）` : '配送中'
  }
  if (status === 'COMPLETED') return role === 'RIDER' ? '已送达' : '已完成'
  if (role === 'RIDER') return riderStatusText(status)
  if (role === 'MERCHANT' && status === 'PAID') return '待骑手接单'
  return orderStatusText(status)
}

export function formatDateTime(value) {
  if (!value) return ''
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatCoord(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(5) : '--'
}

export function formatClock(value = Date.now()) {
  const d = value instanceof Date ? value : new Date(value)
  const pad = (x) => String(x).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function snapshotOf(order) {
  const raw = order?.skuSnapshot
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try { return JSON.parse(raw) } catch { return {} }
}

export function tipGiftOf(code) {
  return TIP_GIFTS.find((g) => g.code === code) || null
}

export const LIVE_TRACK_STATUSES = ['ACCEPTED', 'ARRIVED', 'DELIVERING']
/** 用户/商家订单列表会带 etaMs 的进行中状态；COMPLETED / CANCELLED 不算路。 */
export const LIST_ETA_STATUSES = ['PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING']
export const REFUNDABLE_STATUSES = ['MERCHANT_PENDING', 'PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING', 'COMPLETED', 'REFUND_REJECTED']
export const RIDER_ACTIVE_STATUSES = ['ACCEPTED', 'ARRIVED', 'DELIVERING']
export const DONE_STATUSES = ['COMPLETED', 'CANCELLED', 'REFUNDED']

/** 列表已有 etaMs 时不再打 /track；终态与无 ETA 状态直接跳过。 */
export function needsListTrack(order) {
  if (!order || !LIST_ETA_STATUSES.includes(order.status)) return false
  return order.etaMs == null || order.etaMs === ''
}

export const RIDER_VIEW = {
  PAID: { title: '待骑手接单', hint: '商家备餐中', action: '立即抢单' },
  ACCEPTED: { title: '骑手已接单', hint: '骑手正在赶往商家', action: '到店取餐' },
  ARRIVED: { title: '骑手到店', hint: '已到店，取餐后出发', action: '取餐出发' },
  DELIVERING: { title: '配送中', hint: '正在送往用户', action: '确认送达' },
  COMPLETED: { title: '已送达', hint: '', action: '' }
}

export function riderStatusText(status) {
  return RIDER_VIEW[status]?.title || orderStatusText(status)
}

export function riderStatusHint(status) {
  return RIDER_VIEW[status]?.hint || ''
}

export function riderActionText(status) {
  return RIDER_VIEW[status]?.action || ''
}

export function canUserPay(role, status) {
  return role === 'USER' && status === 'CREATED'
}

export function canUserCancel(role, status) {
  return role === 'USER' && (status === 'CREATED' || status === 'CANCELLING')
}

export function canUserRefund(role, status) {
  return role === 'USER' && REFUNDABLE_STATUSES.includes(status)
}

export function canRiderGrab(role, status) {
  return role === 'RIDER' && status === 'PAID'
}

export function canRiderArrive(role, status) {
  return role === 'RIDER' && status === 'ACCEPTED'
}

export function canRiderDeliver(role, status) {
  return role === 'RIDER' && (status === 'ARRIVED' || status === 'ACCEPTED')
}

export function canRiderComplete(role, status) {
  return role === 'RIDER' && status === 'DELIVERING'
}

export function canMerchantAccept(role, status) {
  return role === 'MERCHANT' && status === 'MERCHANT_PENDING'
}

export function canMerchantAssign(role, status) {
  return role === 'MERCHANT' && status === 'PAID'
}

export function hallOrder(order, riderId) {
  if (!order) return false
  if (order.status === 'PAID' && !order.riderId) return true
  return order.riderId === riderId && RIDER_ACTIVE_STATUSES.includes(order.status)
}
