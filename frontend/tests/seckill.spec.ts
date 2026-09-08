import { describe, expect, it } from 'vitest'
import { matchSeckillOrder, seckillButtonLabel } from '../src/seckill.js'

describe('matchSeckillOrder', () => {
  it('prefers the replayed order id over the first history row', () => {
    const items = [
      { id: 11, activityId: 1, idempotencyKey: '1:1:SECKILL:old' },
      { id: 22, activityId: 1, idempotencyKey: '1:1:SECKILL:1001' }
    ]
    expect(matchSeckillOrder(items, { orderId: 22 }).id).toBe(22)
    expect(matchSeckillOrder(items, { idempotencyKey: '1:1:SECKILL:1001' }).id).toBe(22)
    expect(matchSeckillOrder(items, { orderId: 99 })).toBeNull()
    expect(matchSeckillOrder(items, { activityId: 1, skuId: 9999 })).toBeNull()
  })
})

describe('seckillButtonLabel', () => {
  it('does not say already grabbed when sold out', () => {
    expect(seckillButtonLabel({ soldOut: true, grabbed: false })).toBe('已售罄')
    expect(seckillButtonLabel({ grabbed: true })).toBe('去支付')
  })
})
