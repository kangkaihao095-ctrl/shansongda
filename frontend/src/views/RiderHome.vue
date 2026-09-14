<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import SsdMap from '../components/SsdMap.vue'
import RoleBadge from '../components/RoleBadge.vue'
import OrderToolbar from '../components/OrderToolbar.vue'
import EmptyState from '../components/EmptyState.vue'
import CookingStatus from '../components/CookingStatus.vue'
import { APP_NAME } from '../brand'
import { etaLabel, riderActionText, riderStatusHint, riderStatusText, snapshotOf } from '../status'
import { api } from '../api'
import { RIDER_COVER, imgSrc, onImgError } from '../img'
import { emptyCopy, useOrderQuery } from '../orderQuery'
import { bootRiderLive, riderCanGrabNew, riderLive, riderOnline, riderRemainLabel, riderRemainingHours, riderWorkPct, toggleOnline } from '../riderLive'
import { loadMe, session, toast } from '../session'
import { openNavi } from '../navi'

const router = useRouter()
const grabbing = ref(null)
const acting = ref(null)
const issueText = ref('')
let hallTimer
let liveTimer

const {
  q, size, page, items, pages, hasNext, hasPrev, loading, empty,
  load, setSize, prev, next
} = useOrderQuery(() => ({ scene: 'hall' }))

const grabable = computed(() => items.value.filter((x) => x.status === 'PAID' && !x.riderId))
const current = computed(() => items.value.filter((x) => ['ACCEPTED', 'ARRIVED', 'DELIVERING'].includes(x.status)))

async function attachFocusRoute() {
  const o = current.value[0] || grabable.value[0]
  if (!o) return
  if (o.route?.segments?.length) return
  try {
    const r = (await api(`/api/orders/${o.id}/route`)).data
    o.route = r
    o.etaMs = o.etaMs || r?.etaMs
  } catch { /* 列表已有 etaMs/congestionHint，地图失败再单个兜底 */ }
}

const hallFocus = computed(() => current.value[0] || grabable.value[0])
const hallClusters = computed(() => {
  const buckets = new Map()
  for (const o of grabable.value) {
    if (o.merchantLat == null || o.merchantLon == null) continue
    const key = `${Number(o.merchantLat).toFixed(3)},${Number(o.merchantLon).toFixed(3)}`
    const hit = buckets.get(key) || { lat: o.merchantLat, lon: o.merchantLon, count: 0 }
    hit.count += 1
    buckets.set(key, hit)
  }
  return [...buckets.values()]
})
const hallRoute = computed(() => hallFocus.value?.route || null)
const hallSegments = computed(() => {
  const r = hallRoute.value
  if (!r) return []
  if (r.segments?.length) return r.segments
  return [...(r.riderToMerchant?.segments || []), ...(r.merchantToUser?.segments || [])]
})
const hallPoints = computed(() => hallRoute.value?.points || [])
const hallMerchant = computed(() => hallRoute.value?.waypoints?.merchant
  || (hallFocus.value?.merchantLat != null
    ? { lat: hallFocus.value.merchantLat, lon: hallFocus.value.merchantLon }
    : null))
const hallUser = computed(() => hallRoute.value?.waypoints?.user
  || (hallFocus.value?.userLat != null
    ? { lat: hallFocus.value.userLat, lon: hallFocus.value.userLon }
    : null))
const hallEta = computed(() => hallFocus.value?.etaMs
  ? etaLabel(hallFocus.value.etaMs, hallFocus.value.status)
  : '当前所在位置')
const riderPoint = computed(() => {
  const r = session.me?.rider
  if (r?.lat == null || r?.lon == null) return null
  return { lat: r.lat, lon: r.lon }
})

async function refreshHall() {
  await load()
  await attachFocusRoute()
}

async function refreshLive() {
  for (const o of current.value) {
    try {
      const t = (await api(`/api/orders/${o.id}`)).data
      Object.assign(o, t)
    } catch { /* 进行中单刷新失败保持上一帧 */ }
  }
}

function cover(o) {
  return imgSrc(o.coverUrl || snapshotOf(o).items?.[0]?.imageUrl, 'food', 'order-' + o.id, 'shop')
}

async function grab(id) {
  if (!riderCanGrabNew.value) {
    toast(riderLive.work.forcedOffline ? '今日工时已满或已强制下线，不可接单' : '请先上线', 'err')
    return
  }
  grabbing.value = id
  try {
    await api(`/api/orders/${id}/grab`, { method: 'POST' })
    toast('抢单成功，已加入当前任务')
    await refreshHall()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    grabbing.value = null
  }
}

async function act(path, id) {
  acting.value = id + path
  try {
    await api(`/api/orders/${id}/${path}`, { method: 'POST' })
    toast(path === 'complete' ? '已送达，可在任务中查看' : '已更新')
    await refreshHall()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    acting.value = null
  }
}

function primaryAction(o) {
  if (o.status === 'PAID') return { path: 'grab', label: grabbing.value === o.id ? '抢单中…' : riderActionText(o.status) }
  if (o.status === 'ACCEPTED') return { path: 'arrive', label: riderActionText(o.status) }
  if (o.status === 'ARRIVED') return { path: 'deliver', label: riderActionText(o.status) }
  if (o.status === 'DELIVERING') return { path: 'complete', label: riderActionText(o.status) }
  return null
}

function naviOf(o) {
  const toUser = o.status === 'ARRIVED' || o.status === 'DELIVERING'
  return {
    lat: toUser ? o.userLat : o.merchantLat,
    lon: toUser ? o.userLon : o.merchantLon,
    name: toUser ? o.addressDetail : '取餐点'
  }
}

async function reportIssue(o) {
  try {
    await api(`/api/orders/${o.id}/rider-issue`, {
      method: 'POST',
      body: { code: 'ADDRESS', text: issueText.value || '地址异常' }
    })
    toast('已上报商家')
    issueText.value = ''
  } catch (e) {
    toast(e.message, 'err')
  }
}

onMounted(async () => {
  await loadMe()
  await bootRiderLive()
  await refreshHall()
  hallTimer = setInterval(refreshHall, 10000)
  liveTimer = setInterval(refreshLive, 3000)
})
onUnmounted(() => {
  clearInterval(hallTimer)
  clearInterval(liveTimer)
})
</script>

<template>
  <div class="phone page theme-rider">
    <header class="frost pad">
      <div class="brand-row">
        <img class="brand-mark" src="/logo.svg" :alt="APP_NAME" />
        <div>
          <div class="row">
            <b>骑手工作台</b>
            <RoleBadge />
          </div>
          <div class="slogan">{{ session.me?.displayName }} · 接单大厅</div>
        </div>
      </div>
    </header>
    <div class="phone-body">
      <img class="hero-cover hall-cover" :src="RIDER_COVER" alt="" :class="{ 'is-off': !riderOnline }" />
      <div class="pad hall-ops">
        <div v-if="riderLive.work.forcedOffline" class="card" style="margin-bottom:10px;color:#C43D2F">今日工时已满，已强制下线，次日可再上线</div>
        <div class="duty-compact" :class="riderOnline ? 'duty-on' : 'duty-off'">
          <div style="flex:1;min-width:0">
            <div class="row" style="justify-content:space-between">
              <div class="duty-badge">
                <span class="pulse-dot" :class="{ off: !riderOnline }"></span>
                {{ riderOnline ? '接单中' : '休息中' }}
              </div>
              <span class="muted">剩余 {{ riderRemainingHours }}h</span>
            </div>
            <div class="work-line">
              <div class="work-track"><i :style="{ width: riderWorkPct + '%' }"></i></div>
              <span class="muted">{{ riderRemainLabel }} / 8h</span>
            </div>
          </div>
          <button class="btn" :class="{ ghost: !riderOnline }" :disabled="riderLive.busy" @click="toggleOnline">
            {{ riderOnline ? '下线' : '上线接单' }}
          </button>
        </div>
        <SsdMap
          v-if="riderPoint"
          compact
          :points="hallPoints"
          :segments="hallSegments"
          :rider="riderPoint"
          :merchant="hallMerchant"
          :user="hallUser"
          :eta="hallEta"
          :hint="hallRoute?.trafficHint"
          :clusters="hallClusters"
        />
        <h3 style="margin:12px 0 8px">接单大厅</h3>
        <OrderToolbar
          v-model:q="q"
          :size="size"
          :page="page"
          :pages="pages"
          :has-next="hasNext"
          :has-prev="hasPrev"
          placeholder="搜索可接/进行中订单号、商家、商品、地址"
          @size="n => setSize(n).then(() => attachFocusRoute())"
          @prev="prev().then(() => attachFocusRoute())"
          @next="next().then(() => attachFocusRoute())"
        />
        <div v-if="loading" class="skel" style="height:80px"></div>
        <template v-if="current.length">
          <h4 class="list-kicker">当前任务</h4>
          <article v-for="o in current" :key="'cur-' + o.id" class="card" style="margin-bottom:10px" @click="router.push('/orders/' + o.id)">
            <div class="row">
              <img :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" style="width:52px;height:52px;border-radius:12px;object-fit:cover" />
              <div style="flex:1">
                <div class="row" style="justify-content:space-between">
                  <b>{{ snapshotOf(o).shopName || '#' + o.id }}</b>
                  <span class="pill">{{ riderStatusText(o.status) }}</span>
                </div>
                <div class="muted">{{ riderStatusHint(o.status) }}</div>
                <CookingStatus v-if="o.status === 'ACCEPTED'" compact style="margin:6px 0" />
                <div class="muted">{{ o.addressDetail }}</div>
                <div class="muted">{{ etaLabel(o.etaMs, o.status) }}{{ o.congestionHint ? ' · ' + o.congestionHint : '' }}</div>
              </div>
            </div>
            <button
              v-if="primaryAction(o)"
              class="btn"
              style="width:100%;margin-top:10px"
              :disabled="acting === o.id + primaryAction(o).path"
              @click.stop="act(primaryAction(o).path, o.id)"
            >{{ primaryAction(o).label }}</button>
            <button class="btn ghost" style="width:100%;margin-top:8px" type="button" @click.stop="openNavi(naviOf(o).lat, naviOf(o).lon, naviOf(o).name)">导航</button>
            <div class="row" style="margin-top:8px" @click.stop>
              <input v-model="issueText" placeholder="联系不上 / 地址错误" class="field" style="flex:1;margin:0" />
              <button class="btn ghost" type="button" @click="reportIssue(o)">上报</button>
            </div>
          </article>
        </template>
        <h4 class="list-kicker">可接订单</h4>
        <p v-if="grabable.length && !riderCanGrabNew" class="muted" style="margin:0 0 8px">
          {{ riderLive.work.forcedOffline ? '今日工时已满，不可再抢新单' : '请先上线后再抢单' }}
        </p>
        <article v-for="o in grabable" :key="o.id" class="card" style="margin-bottom:10px" @click="router.push('/orders/' + o.id)">
          <div class="row">
            <img :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" style="width:52px;height:52px;border-radius:12px;object-fit:cover" />
            <div style="flex:1">
              <div class="row" style="justify-content:space-between">
                <b>{{ snapshotOf(o).shopName || '闪送达商家' }}</b>
                <span>
                  <span v-if="o.alongWay" class="pill">顺路</span>
                  <span class="pill">{{ riderStatusText(o.status) }}</span>
                </span>
              </div>
              <div class="muted">{{ o.addressDetail }}</div>
              <div class="muted">{{ etaLabel(o.etaMs, o.status) }}{{ o.congestionHint ? ' · ' + o.congestionHint : '' }}</div>
              <div v-if="o.routeCost != null" class="muted">预计耗时 {{ Math.max(1, Math.round(Number(o.routeCost))) }} 分钟</div>
            </div>
          </div>
          <button class="btn" style="width:100%;margin-top:10px" :disabled="grabbing===o.id || !riderCanGrabNew" @click.stop="grab(o.id)">
            {{ grabbing === o.id ? '抢单中…' : (riderCanGrabNew ? riderActionText(o.status) : '请先上线') }}
          </button>
        </article>
        <EmptyState v-if="empty" title="大厅暂无订单" :hint="emptyCopy('RIDER', 'hall')" />
      </div>
    </div>
    <TabBar />
  </div>
</template>
