import { reactive } from 'vue'

/**
 * 桌面展台右栏的“当前聚焦订单”，由订单详情 / 收银台 / 骑手大厅写入，
 * 用于在状态机与分片图上高亮真实订单。移动端不渲染展台，不影响业务。
 */
export const stage = reactive({
  order: null
})

export function focusOrder(order) {
  if (!order?.id) return
  stage.order = { id: order.id, status: order.status, shopName: order.shopName || '' }
}

export function clearFocus(id) {
  if (id == null || String(stage.order?.id) === String(id)) stage.order = null
}

/** 与后端 OrderSharding 一致：db = id % 4，table = (id / 4) % 8 */
export function shardOf(id) {
  if (id == null || id === '') return null
  try {
    const n = BigInt(String(id))
    return { db: Number(n % 4n), table: Number((n / 4n) % 8n) }
  } catch {
    return null
  }
}
