import { yuan } from './brand'

export function shopIsOpen(merchant) {
  if (!merchant) return true
  if (merchant.open === false) return false
  return merchant.onlineStatus !== 'OFFLINE'
}

export function groupSkus(items) {
  const map = new Map()
  for (const sku of items || []) {
    const key = sku.groupName || '推荐'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(sku)
  }
  return [...map.entries()].map(([name, skus]) => ({ name, skus }))
}

export function centsFromYuan(value) {
  const n = Number(value)
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.round(n * 100)
}

export function yuanDraft(cents) {
  return ((Number(cents) || 0) / 100).toFixed(2)
}

export { chartLabelStep } from './riderReport'

export function reportSummary(stats) {
  if (stats?.summary) return stats.summary
  const title = stats?.range === '1y' ? '近一年' : stats?.range === '30d' ? '近一月' : '近七日'
  const series = stats?.series || []
  const orders = series.reduce((s, p) => s + (Number(p.orderCount) || 0), 0)
  const gmv = series.reduce((s, p) => s + (Number(p.gmvCents) || 0), 0)
  const completed = series.reduce((s, p) => s + (Number(p.completedCount) || 0), 0)
  const refund = series.reduce((s, p) => s + (Number(p.refundCents) || 0), 0)
  let peakDate = ''
  let peakOrders = 0
  for (const p of series) {
    const n = Number(p.orderCount) || 0
    if (n > peakOrders) {
      peakOrders = n
      peakDate = p.date || ''
    }
  }
  const top = (stats?.topSkus || []).slice(0, 3)
  const hot = top.length
    ? `热销 Top${top.length}：${top.map((t) => `${t.name}×${t.qty}`).join('、')}。`
    : '暂无热销商品。'
  const peak = peakOrders > 0 && peakDate
    ? `高峰日 ${peakDate}（${peakOrders} 单）。`
    : '高峰日尚未形成。'
  const aov = stats?.avgPayCents != null ? `客单价 ${yuan(stats.avgPayCents)}。` : ''
  const rate = stats?.refundRate != null ? `退款率 ${Math.round(Number(stats.refundRate) * 1000) / 10}%。` : ''
  return `${title}共 ${orders} 单，成交 ${yuan(gmv)}，完成 ${completed} 单，退款 ${yuan(refund)}。${aov}${rate}${hot}${peak}`
}
