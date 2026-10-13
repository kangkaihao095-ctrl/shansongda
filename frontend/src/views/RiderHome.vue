<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import SsdMap from '../components/SsdMap.vue'
import RoleBadge from '../components/RoleBadge.vue'
import OrderToolbar from '../components/OrderToolbar.vue'
import EmptyState from '../components/EmptyState.vue'
import CookingStatus from '../components/CookingStatus.vue'
import PageHeader from '../components/PageHeader.vue'
import Icon from '../components/Icon.vue'
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
  load, setSize, prev, next,
  loaded, error: hallError
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

/* —— 以下为纯展示状态 —— */
const scrolled = ref(false)
const issueFor = ref(null)
const reportingId = ref(null)
const TASK_STEPS = ['已接单', '已到店', '配送中', '已送达']
const STEP_INDEX = { ACCEPTED: 0, ARRIVED: 1, DELIVERING: 2, COMPLETED: 3 }
const ACTION_ICON = { grab: 'bolt', arrive: 'store', deliver: 'package', complete: 'check-circle' }
const workHot = computed(() => riderWorkPct.value >= 85)
const riderName = computed(() => session.me?.displayName || '骑手')

function onScroll(e) {
  scrolled.value = e.target.scrollTop > 120
}

function stepClass(o, i) {
  const cur = STEP_INDEX[o.status] ?? 0
  if (i < cur) return 'done'
  if (i === cur) return 'current'
  return ''
}

function toUser(o) {
  return o.status === 'ARRIVED' || o.status === 'DELIVERING'
}

function toggleIssue(o) {
  issueFor.value = issueFor.value === o.id ? null : o.id
}

async function submitIssue(o) {
  reportingId.value = o.id
  await reportIssue(o)
  reportingId.value = null
  if (!issueText.value) issueFor.value = null
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
  <div class="phone page rider-hall">
    <PageHeader overlay :solid="scrolled">
      <template #title>
        <span class="hh-brand">
          <img class="hh-logo" src="/logo.svg" :alt="APP_NAME" />
          <span class="hh-text">
            <b>接单大厅</b>
            <small>{{ riderName }} · {{ riderOnline ? '接单中' : '休息中' }}</small>
          </span>
        </span>
      </template>
      <template #right>
        <RoleBadge />
      </template>
    </PageHeader>

    <div class="phone-body" @scroll.passive="onScroll">
      <section class="hall-hero">
        <img class="hero-cover" :class="{ 'is-off': !riderOnline }" :src="RIDER_COVER" alt="" />
        <div class="hero-copy">
          <span class="hero-kicker">{{ APP_NAME }} · 骑手工作台</span>
          <h1 class="hero-title">你好，{{ riderName }}</h1>
          <p class="hero-sub">{{ riderOnline ? '保持在线，附近新单会实时推送到大厅' : '当前休息中，上线后即可开始抢单' }}</p>
        </div>
        <div class="hero-stats">
          <div class="hero-stat">
            <small>可接订单</small>
            <b class="num">{{ loaded ? grabable.length : '—' }}</b>
          </div>
          <div class="hero-stat">
            <small>进行中</small>
            <b class="num">{{ loaded ? current.length : '—' }}</b>
          </div>
          <div class="hero-stat">
            <small>剩余工时</small>
            <b class="num">{{ riderRemainingHours }}<em>h</em></b>
          </div>
        </div>
      </section>

      <div class="pad">
        <section class="duty" :class="{ 'is-on': riderOnline }" aria-label="接单状态">
          <div class="duty-top">
            <span class="duty-ico" aria-hidden="true"><Icon name="power" :size="22" :stroke="2.2" /></span>
            <div class="grow">
              <div class="duty-state">
                <span class="pulse-dot" :class="{ off: !riderOnline }"></span>
                {{ riderOnline ? '接单中' : '休息中' }}
              </div>
              <p class="duty-sub">{{ riderOnline ? '系统正在为你匹配顺路订单' : '上线后自动开始计时与派单' }}</p>
            </div>
            <button
              class="btn duty-btn"
              :class="{ ghost: riderOnline }"
              type="button"
              :aria-pressed="riderOnline"
              :disabled="riderLive.busy"
              @click="toggleOnline"
            >
              <span v-if="riderLive.busy" class="spinner"></span>
              <Icon v-else :name="riderOnline ? 'moon' : 'zap'" :size="16" :stroke="2.2" />
              {{ riderOnline ? '下线' : '上线接单' }}
            </button>
          </div>
          <div class="duty-work">
            <div class="duty-work-head">
              <span>今日剩余工时</span>
              <span><b class="num">{{ riderRemainLabel }}</b> / 8h</span>
            </div>
            <div class="progress" :class="{ 'is-hot': workHot }" role="progressbar" :aria-valuenow="riderWorkPct" aria-valuemin="0" aria-valuemax="100" aria-label="今日工时占用">
              <i :style="{ width: riderWorkPct + '%' }"></i>
            </div>
            <div class="duty-work-foot">
              <span>已用 <b class="num">{{ riderWorkPct }}%</b></span>
              <span>剩余约 <b class="num">{{ riderRemainingHours }}h</b></span>
            </div>
          </div>
        </section>

        <div v-if="riderLive.work.forcedOffline" class="alert-strip" role="alert">
          <Icon name="alert" :size="18" :stroke="2.2" />
          <div>
            <b>今日工时已满，已强制下线</b>
            <span>为保障安全，单日在线上限 8 小时，次日可再上线</span>
          </div>
        </div>

        <section class="section">
          <div class="section-head">
            <h3 class="section-title"><Icon name="map" :size="18" class="text-accent" />附近热区</h3>
            <span class="section-more">{{ hallClusters.length ? hallClusters.length + ' 个取餐热点' : hallEta }}</span>
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
          <div v-else class="map-empty">
            <span class="map-empty-ico" aria-hidden="true"><Icon name="target" :size="22" /></span>
            <div>
              <b>暂未获取到定位</b>
              <p class="muted">上线后会自动上报位置，并在这里显示附近取餐热点</p>
            </div>
          </div>
        </section>

        <section class="section">
          <div class="section-head">
            <h3 class="section-title"><Icon name="bolt" :size="18" class="text-accent" />接单大厅</h3>
            <button
              class="icon-btn sm refresh-btn"
              :class="{ 'is-spinning': loading }"
              type="button"
              aria-label="刷新大厅"
              :disabled="loading"
              @click="refreshHall"
            >
              <Icon name="refresh" :size="16" :stroke="2.2" />
            </button>
          </div>
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

          <div v-if="!loaded" aria-busy="true" aria-label="加载中">
            <div v-for="n in 2" :key="n" class="skel-card hall-skel">
              <div class="row">
                <div class="skel" style="width:56px;height:56px;border-radius:14px"></div>
                <div class="grow">
                  <div class="skel skel-line" style="width:58%"></div>
                  <div class="skel skel-line mt-2" style="width:36%"></div>
                </div>
              </div>
              <div class="skel mt-3" style="height:52px"></div>
              <div class="skel mt-3" style="height:44px;border-radius:999px"></div>
            </div>
          </div>

          <EmptyState v-else-if="hallError && !items.length" kind="error" title="大厅加载失败" :hint="hallError">
            <button class="btn btn-sm" type="button" @click="refreshHall"><Icon name="refresh" :size="16" />重新加载</button>
          </EmptyState>

          <template v-else>
            <div v-if="current.length" class="group">
              <div class="group-head">
                <span class="group-title"><span class="pulse-dot ping"></span>当前任务</span>
                <span class="group-count num">{{ current.length }}</span>
              </div>
              <div class="stack-3">
                <article
                  v-for="(o, i) in current"
                  :key="'cur-' + o.id"
                  v-reveal="i * 60"
                  class="card is-interactive task-card"
                  @click="router.push('/orders/' + o.id)"
                >
                  <div class="tc-top">
                    <span class="tag"><i class="dot"></i>{{ riderStatusText(o.status) }}</span>
                    <span class="tc-id num">#{{ o.id }}</span>
                  </div>
                  <div class="tc-shop">
                    <img class="thumb" :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" />
                    <div class="grow">
                      <b class="t-title ellipsis">{{ snapshotOf(o).shopName || '#' + o.id }}</b>
                      <p class="muted">{{ riderStatusHint(o.status) }}</p>
                    </div>
                  </div>

                  <ol class="stepper tc-steps" aria-label="配送进度">
                    <li v-for="(s, si) in TASK_STEPS" :key="s" class="step" :class="stepClass(o, si)">
                      <span class="step-dot">
                        <Icon v-if="stepClass(o, si) === 'done'" name="check" :size="13" :stroke="3" />
                        <Icon v-else-if="stepClass(o, si) === 'current'" name="bike" :size="13" :stroke="2.2" />
                      </span>
                      <span class="step-label">{{ s }}</span>
                    </li>
                  </ol>

                  <CookingStatus v-if="o.status === 'ACCEPTED'" compact class="mt-3" />

                  <ul class="tc-info">
                    <li><Icon name="pin" :size="15" /><span>{{ o.addressDetail }}</span></li>
                    <li>
                      <Icon name="clock" :size="15" />
                      <span>{{ etaLabel(o.etaMs, o.status) }}</span>
                      <span v-if="o.congestionHint" class="tag tag-warn">{{ o.congestionHint === '粗估' ? '大致时间' : o.congestionHint }}</span>
                    </li>
                  </ul>

                  <button
                    v-if="primaryAction(o)"
                    class="btn btn-lg btn-block tc-primary"
                    type="button"
                    :disabled="acting === o.id + primaryAction(o).path"
                    @click.stop="act(primaryAction(o).path, o.id)"
                  >
                    <span v-if="acting === o.id + primaryAction(o).path" class="spinner"></span>
                    <Icon v-else :name="ACTION_ICON[primaryAction(o).path]" :size="18" :stroke="2.2" />
                    {{ primaryAction(o).label }}
                  </button>

                  <div class="tc-sub" @click.stop>
                    <button class="btn ghost btn-sm" type="button" @click="openNavi(naviOf(o).lat, naviOf(o).lon, naviOf(o).name)">
                      <Icon name="nav" :size="15" />{{ toUser(o) ? '导航至顾客' : '导航至取餐点' }}
                    </button>
                    <button
                      class="btn btn-sm"
                      :class="issueFor === o.id ? 'btn-danger' : 'ghost'"
                      type="button"
                      :aria-expanded="issueFor === o.id"
                      @click="toggleIssue(o)"
                    >
                      <Icon name="alert" :size="15" />异常上报
                    </button>
                  </div>
                  <transition name="fade">
                    <div v-if="issueFor === o.id" class="tc-issue" @click.stop>
                      <input v-model="issueText" class="field" placeholder="联系不上 / 地址错误" aria-label="异常说明" @keydown.enter="submitIssue(o)" />
                      <button class="btn btn-sm btn-dark" type="button" :disabled="reportingId === o.id" @click="submitIssue(o)">
                        <span v-if="reportingId === o.id" class="spinner"></span>上报
                      </button>
                    </div>
                  </transition>
                </article>
              </div>
            </div>

            <div v-if="grabable.length" class="group">
              <div class="group-head">
                <span class="group-title">可接订单</span>
                <span class="group-count num">{{ grabable.length }}</span>
              </div>
              <div v-if="!riderCanGrabNew" class="notice-strip">
                <Icon name="info" :size="16" />
                {{ riderLive.work.forcedOffline ? '今日工时已满，不可再抢新单' : '请先上线后再抢单' }}
              </div>
              <div class="stack-3">
                <article
                  v-for="(o, i) in grabable"
                  :key="o.id"
                  v-reveal="i * 60"
                  class="card is-interactive grab-card"
                  :class="{ 'is-along': o.alongWay }"
                  @click="router.push('/orders/' + o.id)"
                >
                  <div class="gc-head">
                    <img class="thumb" :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" />
                    <div class="grow">
                      <div class="row-between">
                        <b class="t-title ellipsis">{{ snapshotOf(o).shopName || '闪送达商家' }}</b>
                        <span class="tc-id num">#{{ o.id }}</span>
                      </div>
                      <div class="gc-tags">
                        <span v-if="o.alongWay" class="tag tag-success"><Icon name="route" :size="12" :stroke="2.2" />顺路</span>
                        <span class="tag tag-neutral">{{ riderStatusText(o.status) }}</span>
                        <span v-if="o.congestionHint" class="tag tag-warn"><Icon name="alert" :size="12" :stroke="2.2" />{{ o.congestionHint === '粗估' ? '大致时间' : o.congestionHint }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="gc-metrics">
                    <div v-if="o.routeCost != null" class="gc-metric">
                      <small>预计耗时</small>
                      <b class="num">{{ Math.max(1, Math.round(Number(o.routeCost))) }}<em>分钟</em></b>
                    </div>
                    <div class="gc-metric">
                      <small>送达时间</small>
                      <span class="gc-eta">{{ etaLabel(o.etaMs, o.status) }}</span>
                    </div>
                  </div>

                  <p class="gc-addr"><Icon name="pin" :size="15" /><span class="clamp-2">{{ o.addressDetail }}</span></p>

                  <button
                    class="btn btn-block grab-btn"
                    type="button"
                    :disabled="grabbing === o.id || !riderCanGrabNew"
                    @click.stop="grab(o.id)"
                  >
                    <span v-if="grabbing === o.id" class="spinner"></span>
                    <Icon v-else :name="riderCanGrabNew ? 'bolt' : 'power'" :size="17" :stroke="2.2" />
                    {{ grabbing === o.id ? '抢单中…' : (riderCanGrabNew ? riderActionText(o.status) : '请先上线') }}
                  </button>
                </article>
              </div>
            </div>

            <EmptyState
              v-if="empty"
              :kind="q ? 'search' : (riderOnline ? 'empty' : 'offline')"
              title="大厅暂无订单"
              :hint="q ? '没有匹配“' + q + '”的订单，换个关键词试试' : emptyCopy('RIDER', 'hall')"
            >
              <button v-if="q" class="btn btn-sm ghost" type="button" @click="q = ''">清空搜索</button>
              <button v-else-if="!riderOnline && !riderLive.work.forcedOffline" class="btn btn-sm" type="button" :disabled="riderLive.busy" @click="toggleOnline">
                <span v-if="riderLive.busy" class="spinner"></span>上线接单
              </button>
            </EmptyState>
          </template>
        </section>
      </div>
    </div>
    <TabBar />
  </div>
</template>

<style scoped>
/* —— 叠加头部 —— */
.hh-brand { display: flex; align-items: center; gap: 10px; min-width: 0; }
.hh-logo { width: 32px; height: 32px; border-radius: 10px; flex-shrink: 0; box-shadow: 0 4px 10px -4px rgba(0, 0, 0, 0.35); }
.hh-text { display: flex; flex-direction: column; min-width: 0; line-height: 1.2; }
.hh-text b { font-size: var(--fs-md); font-weight: 800; }
.hh-text small { font-size: var(--fs-xs); opacity: 0.72; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* —— 沉浸式 Hero —— */
.hall-hero {
  position: relative;
  overflow: hidden;
  padding: calc(var(--header-h) + env(safe-area-inset-top, 0px) + 10px) var(--gutter) 84px;
  background: var(--hero-grad);
  color: #fff;
}
.hall-hero::after {
  content: "";
  position: absolute; inset: 0;
  background:
    radial-gradient(70% 90% at 100% 0%, rgba(255, 255, 255, 0.16), transparent 60%),
    radial-gradient(60% 60% at 0% 100%, rgba(0, 0, 0, 0.18), transparent 70%);
  pointer-events: none;
}
.hero-cover {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
  opacity: 0.22;
  mix-blend-mode: luminosity;
  -webkit-mask-image: linear-gradient(100deg, transparent 15%, #000 85%);
          mask-image: linear-gradient(100deg, transparent 15%, #000 85%);
  transition: opacity var(--dur-4) ease, filter var(--dur-4) ease;
}
.hero-cover.is-off { opacity: 0.1; filter: grayscale(1); }
.hero-copy { position: relative; z-index: 1; animation: rise var(--dur-4) var(--ease-out) both; }
.hero-kicker { font-size: var(--fs-xs); font-weight: 700; letter-spacing: 0.12em; color: rgba(255, 255, 255, 0.62); }
.hero-title { margin-top: 6px; font-size: var(--fs-2xl); font-weight: 800; letter-spacing: -0.015em; line-height: var(--lh-tight); }
.hero-sub { margin-top: 6px; font-size: var(--fs-sm); color: rgba(255, 255, 255, 0.72); }
.hero-stats {
  position: relative; z-index: 1;
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
  margin-top: var(--s-4);
  animation: rise var(--dur-4) 80ms var(--ease-out) both;
}
.hero-stat {
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.hero-stat small { display: block; font-size: var(--fs-2xs); font-weight: 600; color: rgba(255, 255, 255, 0.66); }
.hero-stat b { display: block; margin-top: 2px; font-size: var(--fs-xl); font-weight: 800; line-height: 1.1; }
.hero-stat em { font-style: normal; font-size: var(--fs-xs); font-weight: 700; margin-left: 1px; opacity: 0.7; }

/* —— 上线大开关卡 —— */
.duty {
  position: relative; z-index: 2;
  margin-top: -60px;
  padding: 18px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-3);
  overflow: hidden;
  transition: box-shadow var(--dur-3) var(--ease-out);
  animation: rise var(--dur-4) 120ms var(--ease-out) both;
}
.duty::before {
  content: "";
  position: absolute; inset: 0;
  background: radial-gradient(90% 80% at 100% 0%, var(--accent-soft), transparent 70%);
  opacity: 0;
  transition: opacity var(--dur-4) ease;
  pointer-events: none;
}
.duty.is-on { box-shadow: 0 0 0 1.5px color-mix(in srgb, var(--accent) 36%, transparent), var(--sh-3); }
.duty.is-on::before { opacity: 1; }
.duty-top { position: relative; display: flex; align-items: center; gap: 12px; }
.duty-ico {
  width: 48px; height: 48px;
  display: grid; place-items: center;
  border-radius: 16px;
  flex-shrink: 0;
  background: var(--surface-sunken);
  color: var(--text-3);
  transition: background var(--dur-3) ease, color var(--dur-3) ease, box-shadow var(--dur-3) ease;
}
.duty.is-on .duty-ico { background: var(--accent-grad); color: var(--accent-ink); box-shadow: var(--sh-accent); }
.duty-state { display: flex; align-items: center; gap: 8px; font-size: var(--fs-lg); font-weight: 800; letter-spacing: -0.01em; }
.duty-sub { margin-top: 2px; font-size: var(--fs-xs); color: var(--text-2); }
.duty-btn { --btn-h: 46px; padding: 0 18px; flex-shrink: 0; }
.duty-work { position: relative; margin-top: 16px; padding-top: 14px; border-top: 1px dashed var(--line-strong); }
.duty-work-head, .duty-work-foot { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.duty-work-head { font-size: var(--fs-sm); color: var(--text-2); }
.duty-work-head b { font-size: var(--fs-xl); font-weight: 800; color: var(--text); }
.duty-work-foot { font-size: var(--fs-xs); color: var(--text-3); }
.duty-work-foot b { color: var(--text-2); font-weight: 700; }
.duty .progress { height: 8px; margin: 10px 0 8px; }
.progress.is-hot > i { background: linear-gradient(90deg, var(--warning-500), var(--danger-500)); }

/* —— 提示条 —— */
.alert-strip {
  display: flex; align-items: flex-start; gap: 10px;
  margin-top: var(--s-3);
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--danger-50);
  color: var(--danger-600);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--danger-500) 24%, transparent);
  animation: rise var(--dur-3) var(--ease-out) both;
}
.alert-strip .ico { margin-top: 1px; }
.alert-strip b { display: block; font-size: var(--fs-base); font-weight: 800; }
.alert-strip span { display: block; margin-top: 2px; font-size: var(--fs-xs); opacity: 0.85; }
.notice-strip {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: var(--s-3);
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--warning-50);
  color: var(--warning-600);
  font-size: var(--fs-sm); font-weight: 650;
}

/* —— 地图 —— */
.map-empty {
  display: flex; align-items: center; gap: 12px;
  padding: 16px;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
}
.map-empty b { font-size: var(--fs-base); }
.map-empty-ico {
  width: 44px; height: 44px; border-radius: 14px; flex-shrink: 0;
  display: grid; place-items: center;
  background: var(--accent-soft); color: var(--accent-strong);
}

/* —— 列表分组 —— */
.refresh-btn:disabled { opacity: 0.6; }
.refresh-btn.is-spinning .ico { animation: spin 0.8s linear infinite; }
.group + .group { margin-top: var(--s-5); }
.group-head { display: flex; align-items: center; justify-content: space-between; margin: var(--s-2) 2px var(--s-3); }
.group-title { display: inline-flex; align-items: center; gap: 8px; font-size: var(--fs-sm); font-weight: 800; color: var(--text-2); letter-spacing: 0.04em; }
.group-count {
  min-width: 24px; height: 22px; padding: 0 8px;
  display: inline-grid; place-items: center;
  border-radius: var(--r-pill);
  background: var(--ink-900); color: #fff;
  font-size: var(--fs-xs); font-weight: 800;
}
.stack-3 > .card + .card { margin-top: 0; }
.hall-skel + .hall-skel { margin-top: var(--s-3); }

.thumb { width: 56px; height: 56px; border-radius: 14px; object-fit: cover; flex-shrink: 0; background: var(--ink-150); box-shadow: 0 0 0 1px var(--line); }
.tc-id { font-size: var(--fs-xs); font-weight: 700; color: var(--text-3); flex-shrink: 0; }

/* —— 当前任务卡 —— */
.task-card {
  padding: 16px;
  border-radius: var(--r-xl);
  box-shadow: 0 0 0 1.5px color-mix(in srgb, var(--accent) 30%, transparent), var(--sh-2);
  overflow: hidden;
}
.task-card::before {
  content: "";
  position: absolute; left: 0; right: 0; top: 0;
  height: 3px;
  background: var(--accent-grad);
}
.tc-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.tc-shop { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.tc-shop .muted { margin-top: 2px; }
.tc-steps {
  list-style: none;
  margin: 14px 0 0;
  padding: 12px 0 10px;
  border-radius: var(--r-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--line);
}
.tc-info { list-style: none; margin: 12px 0 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.tc-info li { display: flex; align-items: flex-start; gap: 8px; font-size: var(--fs-sm); color: var(--text-2); line-height: 1.45; }
.tc-info li .ico { margin-top: 1px; color: var(--text-3); }
.tc-info .tag { margin-left: auto; }
.tc-primary { margin-top: 14px; }
.tc-sub { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 8px; }
.tc-issue { display: flex; gap: 8px; margin-top: 10px; }
.tc-issue .field { height: 40px; font-size: var(--fs-sm); }
.tc-issue .btn { --btn-h: 40px; flex-shrink: 0; }

/* —— 可接订单卡 —— */
.grab-card { border-radius: var(--r-xl); }
.grab-card.is-along { box-shadow: inset 3px 0 0 var(--success-500), var(--sh-1); }
@media (hover: hover) { .grab-card.is-along.is-interactive:hover { box-shadow: inset 3px 0 0 var(--success-500), var(--sh-3); } }
.gc-head { display: flex; gap: 12px; }
.gc-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.gc-metrics {
  display: flex; align-items: stretch; gap: 12px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--line);
}
.gc-metric { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.gc-metric + .gc-metric { padding-left: 12px; border-left: 1px solid var(--line); }
.gc-metric small { font-size: var(--fs-2xs); font-weight: 600; color: var(--text-3); }
.gc-metric b { font-size: var(--fs-xl); font-weight: 800; color: var(--accent-strong); line-height: 1.15; }
.gc-metric em { font-style: normal; font-size: var(--fs-xs); font-weight: 700; margin-left: 2px; color: var(--text-2); }
.gc-eta { margin-top: 2px; font-size: var(--fs-sm); font-weight: 700; color: var(--text); }
.gc-addr { display: flex; align-items: flex-start; gap: 6px; margin-top: 10px; font-size: var(--fs-sm); color: var(--text-2); line-height: 1.45; }
.gc-addr .ico { margin-top: 1px; color: var(--text-3); }
.grab-btn { margin-top: 12px; }
</style>
