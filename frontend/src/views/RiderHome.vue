<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import WorkRing from '../components/WorkRing.vue'
import SsdMap from '../components/SsdMap.vue'
import RoleBadge from '../components/RoleBadge.vue'
import OrderToolbar from '../components/OrderToolbar.vue'
import EmptyState from '../components/EmptyState.vue'
import CookingStatus from '../components/CookingStatus.vue'
import { APP_NAME, yuan } from '../brand'
import { etaLabel, formatClock, formatCoord, riderActionText, riderStatusHint, riderStatusText, snapshotOf } from '../status'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { emptyCopy, useOrderQuery } from '../orderQuery'
import { loadMe, session, toast } from '../session'

const router = useRouter()
const work = ref({})
const earn = ref({})
const busy = ref(false)
const grabbing = ref(null)
const acting = ref(null)
const lastLoc = ref(null)
let timer

const {
  q, size, page, items, pages, hasNext, hasPrev, loading, empty,
  load, setSize, prev, next
} = useOrderQuery(() => ({ scene: 'hall' }))

const online = computed(() => session.me?.rider?.onlineStatus === 'ONLINE')
const remaining = computed(() => Math.max(0, (work.value.remainingSeconds || 0) / 3600).toFixed(1))
const income = computed(() => (earn.value.monthFreightCents || 0) + (work.value.subsidyCents || 0))
const grabable = computed(() => items.value.filter((x) => x.status === 'PAID' && !x.riderId))
const current = computed(() => items.value.filter((x) => ['ACCEPTED', 'ARRIVED', 'DELIVERING'].includes(x.status)))

async function attachEta(list) {
  await Promise.all((list || []).map(async (o) => {
    try {
      const t = (await api(`/api/orders/${o.id}/track`)).data
      o.etaMs = t.etaMs
      if (!o.etaMs) o.etaMs = (await api(`/api/orders/${o.id}/route`)).data.etaMs
    } catch { o.etaMs = null }
  }))
}

async function refresh() {
  try {
    const [w, e] = await Promise.all([
      api('/api/riders/me/work-stats'),
      api('/api/rider/stats').catch(() => ({ data: {} }))
    ])
    work.value = { ...w.data, ...(e.data || {}) }
    if (e.data) {
      work.value.subsidyCents = (w.data.dailySubsidyCents || 0) + (w.data.completeSubsidyCents || 0) * (e.data.completedToday || 0)
    }
    earn.value = e.data || {}
    await load()
    await attachEta(items.value)
  } catch (err) {
    toast(err.message, 'err')
  }
}

async function toggle() {
  busy.value = true
  try {
    await api('/api/riders/me/status', {
      method: 'PUT',
      body: { onlineStatus: online.value ? 'OFFLINE' : 'ONLINE', acceptStatus: 'IDLE' }
    })
    await loadMe()
    await refresh()
    toast(online.value ? '已上线，开始接单' : '已下线休息')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

async function bump() {
  if (!online.value) return toast('上线后才能上报位置', 'err')
  try {
    const r = session.me.rider
    await api('/api/riders/me/location', { method: 'PUT', body: { lat: r.lat + 0.0008, lon: r.lon + 0.0008 } })
    await loadMe()
    lastLoc.value = { lat: session.me.rider.lat, lon: session.me.rider.lon, at: Date.now() }
    toast(`位置已更新 ${formatCoord(session.me.rider.lat)}, ${formatCoord(session.me.rider.lon)}`)
  } catch (e) {
    toast(e.message, 'err')
  }
}

function cover(o) {
  return imgSrc(o.coverUrl || snapshotOf(o).items?.[0]?.imageUrl, 'food', 'order-' + o.id, 'shop')
}

async function grab(id) {
  grabbing.value = id
  try {
    await api(`/api/orders/${id}/grab`, { method: 'POST' })
    toast('抢单成功，已加入当前任务')
    await refresh()
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
    await refresh()
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

onMounted(async () => {
  await loadMe()
  lastLoc.value = session.me?.rider ? { lat: session.me.rider.lat, lon: session.me.rider.lon, at: Date.now() } : null
  await refresh()
  timer = setInterval(refresh, 8000)
})
onUnmounted(() => clearInterval(timer))
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
          <div class="slogan">{{ session.me?.displayName }} · 骑手中心</div>
        </div>
      </div>
    </header>
    <div class="phone-body">
      <img class="hero-cover" src="/images/rider-cover.jpg" alt="" :class="{ 'is-off': !online }" />
      <div class="pad">
        <div v-if="work.forcedOffline" class="card" style="margin-bottom:12px;color:#C43D2F">今日工时已满，已强制下线，次日可再上线</div>
        <div class="duty-card" :class="online ? 'duty-on' : 'duty-off'">
          <div class="duty-badge">
            <span class="pulse-dot" :class="{ off: !online }"></span>
            {{ online ? '接单中' : '休息中' }}
          </div>
          <div class="muted" style="margin-top:6px">{{ online ? '在线可抢大厅订单，位置持续有效' : '离线不会被派单，上线后再接' }}</div>
          <div class="row" style="margin-top:12px">
            <button class="btn" :class="{ ghost: !online }" :disabled="busy" @click="toggle">{{ online ? '下线休息' : '上线接单' }}</button>
            <button class="btn ghost" :class="{ dim: !online }" :disabled="!online" @click="bump">上报位置</button>
          </div>
        </div>
        <div class="card" style="display:flex;gap:16px;align-items:center;margin:14px 0">
          <WorkRing :worked="work.workedSecondsToday || 0" :max="work.maxWorkSeconds || 28800" />
          <div>
            <div class="muted">剩余工作时间</div>
            <div class="num" style="font-size:28px;font-weight:800">{{ remaining }}h</div>
            <div style="font-size:20px;font-weight:800;margin-top:6px">预计收入 {{ yuan(income) }}</div>
            <div class="muted">本月运费 {{ yuan(earn.monthFreightCents) }} · 违约记账 {{ yuan(earn.monthPenaltyCents) }}</div>
            <div class="muted">{{ earn.monthIncomeNote || work.subsidyNote || '日补贴 + 完成单补贴' }}</div>
          </div>
        </div>
        <div v-if="lastLoc" class="card loc-card" :class="{ dim: !online }" style="margin-bottom:12px">
          <div>最新位置 {{ formatCoord(lastLoc.lat) }}, {{ formatCoord(lastLoc.lon) }}</div>
          <div class="muted">上报时间 {{ formatClock(lastLoc.at) }}</div>
        </div>
        <SsdMap
          v-if="session.me?.rider"
          :rider="{ lat: session.me.rider.lat, lon: session.me.rider.lon }"
          eta="当前所在位置"
        />
        <h3 style="margin:18px 0 10px">接单大厅</h3>
        <OrderToolbar
          v-model:q="q"
          :size="size"
          :page="page"
          :pages="pages"
          :has-next="hasNext"
          :has-prev="hasPrev"
          placeholder="搜索可接/进行中订单号、商家、商品、地址"
          @size="n => setSize(n).then(() => attachEta(items))"
          @prev="prev().then(() => attachEta(items))"
          @next="next().then(() => attachEta(items))"
        />
        <div v-if="loading" class="skel" style="height:80px"></div>
        <template v-if="current.length">
          <h4 class="list-kicker">当前任务</h4>
          <article v-for="o in current" :key="'cur-' + o.id" class="card" style="margin-bottom:10px" @click="router.push('/orders/' + o.id)">
            <div class="row">
              <img :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" style="width:52px;height:52px;border-radius:12px;object-fit:cover" />
              <div style="flex:1">
                <div class="row" style="justify-content:space-between">
                  <b>#{{ o.id }}</b>
                  <span class="pill">{{ riderStatusText(o.status) }}</span>
                </div>
                <div class="muted">{{ riderStatusHint(o.status) }}</div>
                <CookingStatus v-if="o.status === 'PAID' || o.status === 'ACCEPTED'" compact style="margin:6px 0" />
                <div class="muted">{{ o.addressDetail }}</div>
                <div class="muted">{{ etaLabel(o.etaMs, o.status) }}</div>
              </div>
            </div>
            <button
              v-if="primaryAction(o)"
              class="btn"
              style="width:100%;margin-top:10px"
              :disabled="acting === o.id + primaryAction(o).path"
              @click.stop="act(primaryAction(o).path, o.id)"
            >{{ primaryAction(o).label }}</button>
          </article>
        </template>
        <h4 class="list-kicker">可接订单</h4>
        <article v-for="o in grabable" :key="o.id" class="card" style="margin-bottom:10px" @click="router.push('/orders/' + o.id)">
          <div class="row">
            <img :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" style="width:52px;height:52px;border-radius:12px;object-fit:cover" />
            <div style="flex:1">
              <div class="row" style="justify-content:space-between">
                <b>#{{ o.id }}</b>
                <span class="pill">{{ riderStatusText(o.status) }}</span>
              </div>
              <div class="muted">{{ riderStatusHint(o.status) }}</div>
              <CookingStatus v-if="o.status === 'PAID'" compact style="margin:6px 0" />
              <div class="muted">{{ snapshotOf(o).shopName || '闪送达商家' }} · {{ o.addressDetail }}</div>
              <div class="muted">{{ etaLabel(o.etaMs, o.status) }}</div>
            </div>
          </div>
          <button class="btn" style="width:100%;margin-top:10px" :disabled="grabbing===o.id" @click.stop="grab(o.id)">
            {{ grabbing === o.id ? '抢单中…' : riderActionText(o.status) }}
          </button>
        </article>
        <EmptyState v-if="empty" title="大厅暂无订单" :hint="emptyCopy('RIDER', 'hall')" />
      </div>
    </div>
    <TabBar />
  </div>
</template>
