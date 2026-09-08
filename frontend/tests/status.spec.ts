import { describe, expect, it } from 'vitest'
import {
  canMerchantAssign,
  canRiderGrab,
  canUserCancel,
  canUserPay,
  etaLabel,
  hallOrder,
  orderStatusHeadline,
  orderStatusText,
  riderActionText,
  riderStatusText,
  TIP_GIFTS
} from '../src/status.js'
import { APP_NAME, yuan, yuanExact } from '../src/brand.js'

describe('orderStatusText', () => {
  it('maps fulfillment statuses', () => {
    expect(orderStatusText('CREATED')).toBe('待支付')
    expect(orderStatusText('MERCHANT_PENDING')).toBe('待商家接单')
    expect(orderStatusText('PAID')).toBe('商家已接单·备餐中')
    expect(orderStatusText('ACCEPTED')).toBe('骑手已接单')
    expect(orderStatusText('ARRIVED')).toBe('骑手到店')
    expect(orderStatusText('DELIVERING')).toBe('配送中')
    expect(orderStatusText('COMPLETED')).toBe('已完成')
    expect(orderStatusText('REFUND_REJECTED')).toBe('退款拒绝')
  })
})

describe('role actions', () => {
  it('keeps pay/cancel on the user and grab on idle hall orders', () => {
    expect(canUserPay('USER', 'CREATED')).toBe(true)
    expect(canUserPay('RIDER', 'CREATED')).toBe(false)
    expect(canUserCancel('USER', 'CANCELLING')).toBe(true)
    expect(canRiderGrab('RIDER', 'PAID')).toBe(true)
    expect(canMerchantAssign('MERCHANT', 'PAID')).toBe(true)
  })

  it('filters rider hall to grabable and own in-progress', () => {
    expect(hallOrder({ status: 'PAID', riderId: null }, 2)).toBe(true)
    expect(hallOrder({ status: 'COMPLETED', riderId: 2 }, 2)).toBe(false)
    expect(hallOrder({ status: 'DELIVERING', riderId: 2 }, 2)).toBe(true)
    expect(hallOrder({ status: 'DELIVERING', riderId: 8 }, 2)).toBe(false)
    expect(hallOrder({ status: 'CREATED', riderId: null }, 2)).toBe(false)
    expect(hallOrder({ status: 'REFUNDING', riderId: 2 }, 2)).toBe(false)
  })

  it('maps rider-facing statuses', () => {
    expect(riderStatusText('PAID')).toBe('待骑手接单')
    expect(riderStatusText('ACCEPTED')).toBe('骑手已接单')
    expect(riderStatusText('ARRIVED')).toBe('骑手到店')
    expect(riderStatusText('DELIVERING')).toBe('配送中')
    expect(riderStatusText('COMPLETED')).toBe('已送达')
    expect(riderActionText('PAID')).toBe('立即抢单')
    expect(riderActionText('ACCEPTED')).toBe('到店取餐')
    expect(riderActionText('ARRIVED')).toBe('取餐出发')
    expect(riderActionText('DELIVERING')).toBe('确认送达')
  })
})

describe('labels', () => {
  it('formats money and eta', () => {
    expect(yuan(1990)).toBe('¥19.90')
    expect(yuanExact(123456)).toBe('¥1,234.56')
    expect(APP_NAME).toBe('闪送达')
    expect(etaLabel(120000)).toMatch(/^预计 \d{2}:\d{2} 送达$/)
    expect(etaLabel(0)).toBe('骑手正在赶往商家')
    expect(etaLabel(0, 'PAID')).toBe('商家备餐中')
    expect(etaLabel(null, 'ACCEPTED')).toBe('骑手正在赶往商家')
  })

  it('builds meituan-style headlines', () => {
    expect(orderStatusHeadline('DELIVERING', 120000, 'USER')).toMatch(/^配送中（预计 \d{2}:\d{2} 送达）$/)
    expect(orderStatusHeadline('COMPLETED', 0, 'USER')).toBe('已完成')
    expect(orderStatusHeadline('COMPLETED', 0, 'RIDER')).toBe('已送达')
    expect(orderStatusHeadline('PAID', 0, 'USER')).toBe('商家已接单·备餐中')
    expect(orderStatusHeadline('PAID', 0, 'RIDER')).toBe('待骑手接单')
    expect(orderStatusHeadline('PAID', 0, 'MERCHANT')).toBe('待骑手接单')
  })

  it('keeps four tip gifts and no custom amount', () => {
    expect(TIP_GIFTS.map((g) => g.code)).toEqual(['WATER', 'MILKTEA', 'GIFT', 'CHICKEN'])
    expect(TIP_GIFTS.map((g) => g.cents)).toEqual([200, 500, 1000, 2000])
  })
})
