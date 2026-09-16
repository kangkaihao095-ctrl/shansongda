import { describe, expect, it, vi } from 'vitest'
import { groupSkus, reportSummary, centsFromYuan, yuanDraft, shopIsOpen } from '../src/merchant.js'
import { yuan } from '../src/brand.js'

describe('merchant board helpers', () => {
  it('groups skus by groupName', () => {
    const groups = groupSkus([
      { id: 1, groupName: '主食', name: '面' },
      { id: 2, groupName: '饮料', name: '茶' },
      { id: 3, groupName: '主食', name: '饭' },
      { id: 4, name: '未分组' }
    ])
    expect(groups.map((g) => g.name)).toEqual(['主食', '饮料', '推荐'])
    expect(groups[0].skus).toHaveLength(2)
  })

  it('builds a non-empty 7d summary without SLA numbers', () => {
    const text = reportSummary({
      range: '7d',
      series: [
        { date: '2026-09-01', orderCount: 2, gmvCents: 3000, completedCount: 1, refundCents: 0 },
        { date: '2026-09-02', orderCount: 5, gmvCents: 8000, completedCount: 4, refundCents: 200 }
      ],
      topSkus: [{ name: '杨枝甘露', qty: 8 }, { name: '草莓杯', qty: 3 }]
    })
    expect(text).toContain('近七日')
    expect(text).toContain('7 单')
    expect(text).toContain(yuan(11000))
    expect(text).toContain('杨枝甘露')
    expect(text).toContain('2026-09-02')
    expect(text).not.toMatch(/P95|QPS|SLA/)
  })

  it('still writes copy when there are no orders', () => {
    const text = reportSummary({ range: '7d', series: [], topSkus: [] })
    expect(text.length).toBeGreaterThan(10)
    expect(text).toContain('暂无热销')
  })

  it('converts yuan draft to cents', () => {
    expect(centsFromYuan('12.5')).toBe(1250)
    expect(yuanDraft(1280)).toBe('12.80')
  })

  it('labels 30d as 近一月', () => {
    const text = reportSummary({ range: '30d', series: Array.from({ length: 30 }, (_, i) => ({
      date: '2026-08-' + String(i + 1).padStart(2, '0'),
      orderCount: 1,
      gmvCents: 1000,
      completedCount: 1,
      refundCents: 0
    })) })
    expect(text).toContain('近一月')
    expect(text).toContain('30 单')
  })

  it('treats open=false or OFFLINE as closed', () => {
    expect(shopIsOpen({ onlineStatus: 'ONLINE' })).toBe(true)
    expect(shopIsOpen({ onlineStatus: 'OFFLINE' })).toBe(false)
    expect(shopIsOpen({ open: false, onlineStatus: 'ONLINE' })).toBe(false)
    expect(shopIsOpen(null)).toBe(true)
  })

  it('includes average pay and refund rate from aggregated stats', () => {
    const text = reportSummary({
      range: '7d',
      series: [{ date: '2026-09-01', orderCount: 2, gmvCents: 4000, completedCount: 2, refundCents: 200 }],
      avgPayCents: 2000,
      refundRate: 0.05
    })
    expect(text).toContain('客单价')
    expect(text).toContain(yuan(2000))
    expect(text).toContain('退款率 5%')
    expect(text).not.toMatch(/P95|QPS|SLA/)
  })
})
