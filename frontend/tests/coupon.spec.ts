import { describe, expect, it, beforeEach } from 'vitest'
import {
  couponAmount,
  couponFaceLabel,
  couponNo,
  couponRules,
  couponThreshold,
  couponTone,
  freightCoverText,
  LOGIN_GIFT_SHOWN_KEY,
  loginGiftEligible,
  loginGiftStorageKey,
  markLoginGiftClaimed,
  markLoginGiftDismissed,
  markLoginGiftShown,
  shouldSkipLoginGiftPopup
} from '../src/coupon.js'

describe('coupon helpers', () => {
  it('renders amount and free-threshold', () => {
    expect(couponAmount({ type: 'AMOUNT', discountCents: 1500 })).toBe('15')
    expect(couponFaceLabel({ type: 'AMOUNT', discountCents: 1500 })).toBe('¥15')
    expect(couponFaceLabel({ type: 'PERCENT', percentOff: 8 })).toBe('8折')
    expect(couponThreshold({ minSpendCents: 0 })).toBe('无门槛')
    expect(couponThreshold({ minSpendCents: 2500 })).toBe('满 ¥25 可用')
    expect(couponTone({ memberOnly: true })).toBe('gold')
    expect(couponTone({ icon: 'minus' })).toBe('red')
    expect(couponNo({ couponId: 9, code: 'X' })).toBe(9)
  })

  it('says coupons do not cover freight', () => {
    expect(freightCoverText({ coversFreight: false })).toBe('本券不抵扣运费（只抵商品）')
    expect(freightCoverText({ coversFreight: true })).toBe('本券可抵运费')
    expect(couponRules({ type: 'AMOUNT', minSpendCents: 0, coversFreight: false }).join('')).toContain('不抵扣运费')
    expect(couponRules({ type: 'FREIGHT', coversFreight: true }).join('')).toContain('可抵配送费')
  })

  it('builds login gift storage key', () => {
    expect(loginGiftStorageKey(1, '2026-09-07')).toBe('ssd.loginGift:1:2026-09-07')
  })
})

describe('login gift popup gates', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('skips when session already shown or claimed or dismissed', () => {
    expect(shouldSkipLoginGiftPopup(1, '2026-09-08')).toBe(false)
    markLoginGiftShown(1, '2026-09-08')
    expect(sessionStorage.getItem(LOGIN_GIFT_SHOWN_KEY)).toBe('1:2026-09-08')
    expect(shouldSkipLoginGiftPopup(1, '2026-09-08')).toBe(true)
    sessionStorage.clear()
    markLoginGiftClaimed(1, '2026-09-08')
    expect(shouldSkipLoginGiftPopup(1, '2026-09-08')).toBe(true)
    localStorage.clear()
    markLoginGiftDismissed(1, '2026-09-08')
    expect(shouldSkipLoginGiftPopup(1, '2026-09-08')).toBe(true)
  })

  it('treats alreadyClaimed as not eligible', () => {
    expect(loginGiftEligible({ eligible: true, items: [{ code: 'A' }] })).toBe(true)
    expect(loginGiftEligible({ eligible: false, alreadyClaimed: true, items: [{ code: 'A' }] })).toBe(false)
    expect(loginGiftEligible({ claimed: true, items: [{ code: 'A' }] })).toBe(false)
    expect(loginGiftEligible({ eligible: true, items: [] })).toBe(false)
  })
})
