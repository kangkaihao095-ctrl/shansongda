const AUTO_KEY = (uid) => 'ssd.rider.autoReport:' + uid
const INTERVAL_KEY = (uid) => 'ssd.rider.autoReportInterval:' + uid

export function loadAutoReport(userId, serverFlag) {
  if (serverFlag === true) return true
  if (serverFlag === false) return false
  try {
    return localStorage.getItem(AUTO_KEY(userId)) === '1'
  } catch {
    return false
  }
}

export function saveAutoReport(userId, on) {
  try {
    localStorage.setItem(AUTO_KEY(userId), on ? '1' : '0')
  } catch { /* ignore */ }
}

export function loadReportInterval(userId, serverSec) {
  const n = Number(serverSec)
  if (Number.isFinite(n) && n >= 3 && n <= 30) return Math.round(n)
  try {
    const stored = Number(localStorage.getItem(INTERVAL_KEY(userId)))
    if (Number.isFinite(stored) && stored >= 3 && stored <= 30) return Math.round(stored)
  } catch { /* ignore */ }
  return 5
}

export function saveReportInterval(userId, sec) {
  const n = Math.min(30, Math.max(3, Math.round(Number(sec) || 5)))
  try {
    localStorage.setItem(INTERVAL_KEY(userId), String(n))
  } catch { /* ignore */ }
  return n
}

export function walkLocation(lat, lon, heading) {
  const nextHeading = heading + (Math.random() - 0.5) * 0.7
  const step = 0.00032 + Math.random() * 0.0005
  let nextLat = Number(lat) + Math.cos(nextHeading) * step
  let nextLon = Number(lon) + Math.sin(nextHeading) * step
  nextLat = Math.min(31.32, Math.max(31.18, nextLat))
  nextLon = Math.min(121.56, Math.max(121.40, nextLon))
  return { lat: nextLat, lon: nextLon, heading: nextHeading }
}

export function formatRemain(seconds) {
  const n = Math.max(0, Math.round(Number(seconds) || 0))
  const h = Math.floor(n / 3600)
  const m = Math.floor((n % 3600) / 60)
  const s = n % 60
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function chartLabelStep(n) {
  if (n <= 8) return 1
  if (n <= 16) return 2
  return Math.ceil(n / 6)
}
