import { computed, reactive, watch } from 'vue'
import { api } from './api'
import {
  formatRemain, loadAutoReport, loadReportInterval, saveAutoReport, saveReportInterval, walkLocation
} from './riderReport'
import { loadMe, session, toast } from './session'
import { formatCoord } from './status'

export const riderLive = reactive({
  work: {},
  earn: {},
  charts: { income: [], work: [] },
  lastLoc: null,
  autoReport: false,
  intervalSec: 5,
  reporting: false,
  remainTick: 0,
  busy: false,
  booted: false
})

export const riderOnline = computed(() => session.me?.rider?.onlineStatus === 'ONLINE')
/** 可抢新单：必须在线且未被 8h 强制下线。位置上报开关不参与。 */
export function canGrabNewOrders(onlineStatus, forcedOffline) {
  return onlineStatus === 'ONLINE' && !forcedOffline
}
export const riderCanGrabNew = computed(() =>
  canGrabNewOrders(session.me?.rider?.onlineStatus, riderLive.work.forcedOffline)
)
export const riderRemaining = computed(() => Math.max(0, (riderLive.work.remainingSeconds || 0) - riderLive.remainTick))
export const riderRemainingHours = computed(() => (riderRemaining.value / 3600).toFixed(1))
export const riderWorkPct = computed(() => {
  const max = Math.max(1, riderLive.work.maxWorkSeconds || 28800)
  return Math.min(100, Math.round(((riderLive.work.workedSecondsToday || 0) / max) * 100))
})
export const riderIncome = computed(() =>
  (riderLive.earn.monthFreightCents || 0)
  + (riderLive.work.subsidyCents || 0)
  + (riderLive.charts.monthTipCents || 0)
)
export const riderRemainLabel = computed(() => formatRemain(riderRemaining.value))

let statsTimer
let remainTimer
let reportTimer
let heading = Math.random() * Math.PI * 2
let stopOnlineWatch

export async function refreshStats() {
  try {
    const [w, e, c] = await Promise.all([
      api('/api/riders/me/work-stats'),
      api('/api/rider/stats').catch(() => ({ data: {} })),
      api('/api/riders/me/charts?days=7').catch(() => ({ data: { income: [], work: [] } }))
    ])
    riderLive.work = { ...w.data, ...(e.data || {}) }
    if (e.data) {
      riderLive.work.subsidyCents = (w.data.dailySubsidyCents || 0)
        + (w.data.completeSubsidyCents || 0) * (e.data.completedToday || 0)
    }
    riderLive.earn = e.data || {}
    riderLive.charts = c.data || { income: [], work: [] }
    riderLive.remainTick = 0
  } catch (err) {
    toast(err.message, 'err')
  }
}

function stopReport() {
  if (reportTimer) {
    clearInterval(reportTimer)
    reportTimer = null
  }
  riderLive.reporting = false
}

function startReport() {
  stopReport()
  if (!riderOnline.value || !riderLive.autoReport) return
  const ms = Math.min(30, Math.max(3, Number(riderLive.intervalSec) || 5)) * 1000
  reportTimer = setInterval(() => { bumpLocation(true) }, ms)
}

async function persistReport(on, sec) {
  const uid = session.me?.userId
  saveAutoReport(uid, on)
  if (sec != null) riderLive.intervalSec = saveReportInterval(uid, sec)
  try {
    const res = await api('/api/riders/me/settings', {
      method: 'PUT',
      body: { autoReport: on, intervalSeconds: riderLive.intervalSec }
    })
    if (session.me?.rider) Object.assign(session.me.rider, res.data || {})
  } catch (e) {
    toast(e.message, 'err')
  }
}

export async function toggleReport() {
  if (!riderOnline.value && !riderLive.autoReport) return toast('上线后才能开启位置自动上报', 'err')
  const next = !riderLive.autoReport
  riderLive.autoReport = next
  if (!next) stopReport()
  await persistReport(next)
  if (next) {
    startReport()
    toast('已开启位置自动上报')
  } else {
    toast('已关闭位置自动上报')
  }
}

export async function changeInterval() {
  riderLive.intervalSec = saveReportInterval(session.me?.userId, riderLive.intervalSec)
  if (riderLive.autoReport) {
    await persistReport(true, riderLive.intervalSec)
    startReport()
  }
}

export async function toggleOnline() {
  riderLive.busy = true
  try {
    const goingOffline = riderOnline.value
    await api('/api/riders/me/status', {
      method: 'PUT',
      body: { onlineStatus: goingOffline ? 'OFFLINE' : 'ONLINE', acceptStatus: 'IDLE' }
    })
    await loadMe()
    if (goingOffline) {
      riderLive.autoReport = false
      stopReport()
      saveAutoReport(session.me?.userId, false)
    } else {
      riderLive.autoReport = loadAutoReport(session.me?.userId, session.me?.rider?.autoReport)
      if (riderLive.autoReport) startReport()
    }
    await refreshStats()
    toast(riderOnline.value ? '已上线，开始接单' : '已下线休息')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    riderLive.busy = false
  }
}

export async function bumpLocation(silent = false) {
  if (!riderOnline.value) {
    if (!silent) toast('上线后才能上报位置', 'err')
    return
  }
  try {
    const r = session.me.rider
    const next = walkLocation(r.lat, r.lon, heading)
    heading = next.heading
    riderLive.reporting = true
    const loc = await api('/api/riders/me/location', { method: 'PUT', body: { lat: next.lat, lon: next.lon } })
    const rider = session.me?.rider
    if (rider) {
      rider.lat = loc.data?.lat ?? next.lat
      rider.lon = loc.data?.lon ?? next.lon
      if (loc.data?.updateTime) rider.updateTime = loc.data.updateTime
      if (loc.data?.version != null) rider.version = loc.data.version
    }
    riderLive.lastLoc = { lat: rider?.lat ?? next.lat, lon: rider?.lon ?? next.lon, at: Date.now() }
    if (!silent) toast(`位置已更新 ${formatCoord(session.me.rider.lat)}, ${formatCoord(session.me.rider.lon)}`)
  } catch (e) {
    if (!silent) toast(e.message, 'err')
    if (e.status === 409) {
      riderLive.autoReport = false
      stopReport()
    }
  } finally {
    setTimeout(() => { riderLive.reporting = false }, 900)
  }
}

function clearLiveTimers() {
  if (statsTimer) { clearInterval(statsTimer); statsTimer = null }
  if (remainTimer) { clearInterval(remainTimer); remainTimer = null }
  stopReport()
  if (stopOnlineWatch) { stopOnlineWatch(); stopOnlineWatch = null }
}

export async function bootRiderLive() {
  if (session.me?.role !== 'RIDER') return
  if (riderLive.booted) {
    await refreshStats()
    return
  }
  riderLive.booted = true
  const rider = session.me?.rider
  riderLive.lastLoc = rider ? { lat: rider.lat, lon: rider.lon, at: Date.now() } : null
  const uid = session.me?.userId
  riderLive.intervalSec = loadReportInterval(uid, rider?.autoReportIntervalSec)
  riderLive.autoReport = riderOnline.value && loadAutoReport(uid, rider?.autoReport)
  await refreshStats()
  statsTimer = setInterval(refreshStats, 30000)
  remainTimer = setInterval(() => {
    if (riderOnline.value && riderRemaining.value > 0) riderLive.remainTick += 1
  }, 1000)
  stopOnlineWatch = watch(riderOnline, (on) => {
    if (!on) {
      riderLive.autoReport = false
      stopReport()
    }
  })
  if (riderLive.autoReport) startReport()
}

export function stopRiderLive() {
  riderLive.booted = false
  clearLiveTimers()
}
