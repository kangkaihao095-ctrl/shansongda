<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import SsdMap from './SsdMap.vue'
import CookingStatus from './CookingStatus.vue'
import { etaLabel } from '../status'
import { api, token } from '../api'
import { onImgError } from '../img'
import { session } from '../session'

const LIVE = ['PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING']
const router = useRouter()
const order = ref(null)
const track = ref(null)
const open = ref(false)
const viaSse = ref(false)
let pollTimer
let abort = new AbortController()

const visible = computed(() => session.me?.role === 'USER' && !!order.value)
const etaClock = computed(() => etaLabel(track.value?.etaMs, order.value?.status))
const statusHint = computed(() => {
  const s = order.value?.status
  if (s === 'DELIVERING') return '配送中'
  if (s === 'ARRIVED') return '骑手到店'
  if (s === 'ACCEPTED') return '骑手已接单'
  if (s === 'PAID') return '商家备餐中'
  return ''
})
const cooking = computed(() => order.value?.status === 'PAID' || order.value?.status === 'ACCEPTED')
const riderName = computed(() => track.value?.riderProfile?.displayName || track.value?.rider?.displayName || '闪送达骑手')
const riderAvatar = computed(() => track.value?.riderProfile?.avatarUrl || track.value?.rider?.avatarUrl)
const goods = computed(() => {
  const snap = order.value?.skuSnapshot
  const items = snap?.items || []
  return items.map((it) => it.name).filter(Boolean).slice(0, 2).join('、') || order.value?.skuSnapshot?.shopName || '配送订单'
})
const riderPoint = computed(() => {
  const r = track.value?.rider
  if (r?.lat == null || r?.lon == null) return null
  return { lat: r.lat, lon: r.lon }
})
const userPoint = computed(() => {
  if (track.value?.userLat == null) return null
  return { lat: track.value.userLat, lon: track.value.userLon }
})
const merchantPoint = computed(() => {
  if (track.value?.merchantLat == null) return null
  return { lat: track.value.merchantLat, lon: track.value.merchantLon }
})
const routePts = computed(() => {
  const route = track.value?.route
  if (route?.points?.length) return route.points
  const pts = []
  for (const key of ['riderToUser', 'riderToMerchant', 'merchantToUser', 'points']) {
    const arr = route?.[key]
    if (Array.isArray(arr)) {
      for (const p of arr) {
        if (p?.lat != null) pts.push({ lat: p.lat, lon: p.lon })
      }
    }
  }
  return pts
})
const routeSegs = computed(() => {
  const route = track.value?.route
  if (route?.segments?.length) return route.segments
  return [...(route?.riderToUser?.segments || []), ...(route?.riderToMerchant?.segments || []), ...(route?.merchantToUser?.segments || [])]
})

function applyPayload(data) {
  const o = data?.order
  if (!o || !LIVE.includes(o.status)) {
    order.value = null
    track.value = null
    open.value = false
    return
  }
  order.value = o
  track.value = o
}

async function pollOnce() {
  if (session.me?.role !== 'USER') {
    order.value = null
    track.value = null
    return
  }
  try {
    const data = (await api('/api/orders/active-delivery')).data
    applyPayload(data)
  } catch {
    /* 保持上一帧 */
  }
}

async function startSse() {
  if (session.me?.role !== 'USER') return false
  const t = token()
  if (!t || !abort) return false
  try {
    const res = await fetch('/api/orders/stream-active', {
      headers: { Authorization: 'Bearer ' + t, Accept: 'text/event-stream' },
      signal: abort.signal
    })
    if (!res.ok || !res.body) return false
    viaSse.value = true
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const chunks = buf.split('\n\n')
      buf = chunks.pop() || ''
      for (const chunk of chunks) {
        const line = chunk.split('\n').find((l) => l.startsWith('data:'))
        if (!line) continue
        try {
          applyPayload(JSON.parse(line.slice(5).trim()))
        } catch { /* 忽略半包 */ }
      }
    }
    return true
  } catch (e) {
    if (e?.name === 'AbortError') return true
    viaSse.value = false
    return false
  }
}

function goDetail() {
  if (!order.value) return
  router.push('/orders/' + order.value.id)
}

function stopLive() {
  abort?.abort()
  abort = new AbortController()
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  viaSse.value = false
  order.value = null
  track.value = null
}

async function startUserLive() {
  stopLive()
  if (session.me?.role !== 'USER') return
  await pollOnce()
  ;(async () => {
    for (let i = 0; i < 40 && !abort.signal.aborted; i++) {
      const ok = await startSse()
      if (abort.signal.aborted) return
      if (!ok) break
    }
    if (abort.signal.aborted) return
    viaSse.value = false
    pollTimer = setInterval(pollOnce, 8000)
  })()
}

watch(() => session.me?.role, (role) => {
  if (role === 'USER') startUserLive()
  else stopLive()
}, { immediate: true })
onUnmounted(() => {
  stopLive()
})
</script>

<template>
  <div v-if="visible" class="eta-float" :class="{ open }">
    <button class="eta-mini" type="button" @click="open = !open">
      <img
        v-if="riderAvatar"
        class="eta-ava"
        :src="riderAvatar"
        alt=""
        data-seed="eta-rider"
        @error="onImgError"
      />
      <span v-else class="eta-ava eta-ava-ph">骑</span>
      <div class="eta-copy">
        <b>{{ etaClock }}</b>
        <div class="muted">{{ riderName }} · {{ statusHint }}</div>
      </div>
    </button>
    <div v-if="open" class="eta-panel">
      <div class="eta-map">
        <SsdMap
          :points="routePts"
          :segments="routeSegs"
          :rider="riderPoint"
          :merchant="merchantPoint"
          :user="userPoint"
          :eta="etaClock"
          :hint="route?.trafficHint"
        />
      </div>
      <div class="eta-body">
        <div class="muted">订单 {{ order.id }} · {{ goods }}</div>
        <div style="margin:6px 0">{{ riderName }} · {{ statusHint }}</div>
        <CookingStatus v-if="cooking" compact style="margin:0 0 8px" />
        <button class="btn" style="width:100%" type="button" @click="goDetail">查看订单详情</button>
      </div>
    </div>
  </div>
</template>
