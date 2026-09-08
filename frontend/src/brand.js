import { etaLabel, ORDER_STATUS_LABEL } from './status'

export const APP_NAME = '闪送达'
export const SLOGAN = '更快达，就闪送达'
export const STATUS_LABEL = ORDER_STATUS_LABEL

export function yuan(cents) {
  return `¥${((Number(cents) || 0) / 100).toFixed(2)}`
}

export function yuanExact(cents) {
  const n = (Number(cents) || 0) / 100
  const [i, d] = n.toFixed(2).split('.')
  return `¥${i.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${d}`
}

export function etaText(ms, status) {
  return etaLabel(ms, status)
}

export function initials(name) {
  const text = (name || '闪').trim()
  return text.slice(0, 1)
}
