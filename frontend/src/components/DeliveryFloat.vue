<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SsdMap from './SsdMap.vue'
import CookingStatus from './CookingStatus.vue'
import Icon from './Icon.vue'
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

const route = useRoute()
const visible = computed(() => session.me?.role === 'USER' && !!order.value)
/* 已在该订单详情页时不再重复悬浮，避免遮挡页面主信息 */
const onDetail = computed(() => !!order.value && route.path === '/orders/' + order.value.id)
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
  <div v-if="visible && !onDetail" class="eta-float" :class="{ open }">
    <transition name="pop">
      <div v-if="open" class="eta-panel" role="dialog" aria-label="配送中订单">
        <div class="eta-map">
          <SsdMap
            :points="routePts"
            :segments="routeSegs"
            :rider="riderPoint"
            :merchant="merchantPoint"
            :user="userPoint"
            :eta="etaClock"
            :hint="track?.route?.trafficHint"
          />
        </div>
        <div class="eta-body">
          <div class="muted ellipsis">订单 {{ order.id }} · {{ goods }}</div>
          <div class="eta-rider">{{ riderName }} · {{ statusHint }}</div>
          <CookingStatus v-if="cooking" compact class="mb-3" />
          <button class="btn btn-sm btn-block" type="button" @click="goDetail">查看订单详情</button>
        </div>
      </div>
    </transition>
    <button class="eta-mini" type="button" :aria-expanded="open" @click="open = !open">
      <span class="eta-ava-wrap">
        <img
          v-if="riderAvatar"
          class="eta-ava"
          :src="riderAvatar"
          alt=""
          data-seed="eta-rider"
          @error="onImgError"
        />
        <span v-else class="eta-ava eta-ava-ph"><Icon name="bike" :size="16" /></span>
        <i class="eta-live" aria-hidden="true"></i>
      </span>
      <span class="eta-copy">
        <b>{{ etaClock }}</b>
        <small>{{ riderName }} · {{ statusHint }}</small>
      </span>
      <Icon :name="open ? 'chevron-down' : 'chevron'" :size="14" class="eta-caret" />
    </button>
  </div>
</template>

<style scoped>
.eta-float {
  position: absolute;
  right: 12px;
  bottom: calc(var(--tabbar-h) + 22px + env(safe-area-inset-bottom, 0px));
  z-index: var(--z-float);
  display: flex; flex-direction: column; align-items: flex-end; gap: 10px;
  pointer-events: none;
}
.eta-mini, .eta-panel { pointer-events: auto; }
.eta-mini {
  display: flex; align-items: center; gap: 10px;
  max-width: 240px;
  padding: 6px 12px 6px 6px;
  border-radius: var(--r-pill);
  background: rgba(22, 20, 18, 0.92);
  color: #fff;
  text-align: left;
  box-shadow: var(--sh-4);
  backdrop-filter: blur(14px);
  animation: rise var(--dur-4) var(--ease-out) both;
  transition: transform var(--dur-1) ease;
}
.eta-mini:active { transform: scale(0.97); }
.eta-ava-wrap { position: relative; flex-shrink: 0; }
.eta-ava { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; display: block; }
.eta-ava-ph { display: grid; place-items: center; background: var(--grad-brand); color: #1A1208; }
.eta-live {
  position: absolute; right: -1px; bottom: -1px;
  width: 11px; height: 11px; border-radius: 50%;
  background: var(--success-500);
  box-shadow: 0 0 0 2px var(--ink-900);
  animation: pulse-ring 1.8s ease-out infinite;
}
.eta-copy { min-width: 0; line-height: 1.25; }
.eta-copy b { display: block; font-size: var(--fs-sm); font-weight: 750; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.eta-copy small { display: block; font-size: var(--fs-2xs); color: rgba(255, 255, 255, 0.62); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.eta-caret { color: rgba(255, 255, 255, 0.5); }
.eta-panel {
  width: 300px;
  border-radius: var(--r-xl);
  background: var(--surface);
  overflow: hidden;
  box-shadow: var(--sh-4);
  transform-origin: bottom right;
}
.eta-map :deep(.map-wrap) { border-radius: 0; box-shadow: none; }
.eta-map :deep(.map-box) { height: 150px; }
.eta-body { padding: 12px 14px 14px; }
.eta-rider { margin: 4px 0 10px; font-weight: 700; font-size: var(--fs-base); }
</style>
