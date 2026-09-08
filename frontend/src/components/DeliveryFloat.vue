<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import SsdMap from './SsdMap.vue'
import CookingStatus from './CookingStatus.vue'
import { etaLabel } from '../status'
import { api } from '../api'
import { onImgError } from '../img'
import { session } from '../session'

const LIVE = ['PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING']
const router = useRouter()
const order = ref(null)
const track = ref(null)
const open = ref(false)
let timer

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
const routePts = computed(() => {
  const route = track.value?.route
  const pts = []
  for (const key of ['riderToMerchant', 'merchantToUser', 'points']) {
    const arr = route?.[key]
    if (Array.isArray(arr)) {
      for (const p of arr) {
        if (p?.lat != null) pts.push({ lat: p.lat, lon: p.lon })
      }
    }
  }
  return pts
})

async function tick() {
  if (session.me?.role !== 'USER') {
    order.value = null
    track.value = null
    return
  }
  try {
    const list = (await api('/api/orders?size=10&page=1')).data?.items || []
    const hit = list.find((o) => LIVE.includes(o.status))
    order.value = hit || null
    if (!hit) {
      track.value = null
      open.value = false
      return
    }
    track.value = (await api(`/api/orders/${hit.id}/track`)).data
  } catch {
    /* 轮询失败保持上一帧 */
  }
}

function goDetail() {
  if (!order.value) return
  router.push('/orders/' + order.value.id)
}

onMounted(() => {
  tick()
  timer = setInterval(tick, 5000)
})
onUnmounted(() => clearInterval(timer))
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
          :rider="riderPoint"
          :user="userPoint"
          :eta="etaClock"
        />
      </div>
      <div class="eta-body">
        <div class="muted">订单 {{ order.id }} · {{ goods }}</div>
        <div style="margin:6px 0">{{ riderName }} · {{ statusHint }}</div>
        <CookingStatus v-if="cooking" compact style="margin:0 0 8px" />
        <p v-if="track?.riderProfile?.bio" class="muted" style="margin:0 0 8px">{{ String(track.riderProfile.bio).slice(0, 48) }}</p>
        <button class="btn" style="width:100%" type="button" @click="goDetail">查看订单详情</button>
      </div>
    </div>
  </div>
</template>
