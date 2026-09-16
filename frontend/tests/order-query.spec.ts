import { describe, expect, it } from 'vitest'
import { DEFAULT_PAGE_SIZE, emptyCopy, PAGE_SIZES } from '../src/orderQuery.js'

describe('order query helpers', () => {
  it('only allows 5/10/20 page sizes like the backend', () => {
    expect(PAGE_SIZES).toEqual([5, 10, 20])
    expect(DEFAULT_PAGE_SIZE).toBe(10)
  })

  it('writes role-aware empty copy', () => {
    expect(emptyCopy('USER')).toBe('还没有外卖订单')
    expect(emptyCopy('RIDER')).toBe('骑手身份下暂无已完成任务')
    expect(emptyCopy('MERCHANT')).toBe('商家身份下暂无店铺订单')
    expect(emptyCopy('RIDER', 'hall')).toBe('骑手身份下暂无可接或进行中的订单')
    expect(emptyCopy('MERCHANT', 'pending')).toBe('商家身份下暂无待接订单')
    expect(emptyCopy('USER', 'done')).toBe('完成后的配送会出现在这里')
  })
})
