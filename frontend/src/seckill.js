export function matchSeckillOrder(items, { orderId, idempotencyKey, skuId, activityId } = {}) {
  const list = Array.isArray(items) ? items : []
  if (orderId != null && orderId !== '') {
    const hit = list.find((o) => String(o.id) === String(orderId))
    if (hit) return hit
  }
  if (idempotencyKey) {
    const hit = list.find((o) => o.idempotencyKey === idempotencyKey)
    if (hit) return hit
  }
  if (skuId != null && activityId != null) {
    return list.find((o) => {
      if (o.activityId !== activityId) return false
      const snap = o.skuSnapshot
      const rows = snap?.items || []
      return rows.some((it) => String(it.skuId) === String(skuId))
    }) || null
  }
  return null
}

export function seckillButtonLabel({ busy, soldOut, grabbed, remain }) {
  if (busy) return '抢购中…'
  if (grabbed) return remain === 0 ? '已抢到' : '去支付'
  if (soldOut) return '已售罄'
  return '立即抢'
}
