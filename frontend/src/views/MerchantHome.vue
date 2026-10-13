<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import SalesChart from '../components/SalesChart.vue'
import RoleBadge from '../components/RoleBadge.vue'
import OrderToolbar from '../components/OrderToolbar.vue'
import EmptyState from '../components/EmptyState.vue'
import MoneyHover from '../components/MoneyHover.vue'
import CookingStatus from '../components/CookingStatus.vue'
import PageHeader from '../components/PageHeader.vue'
import BottomSheet from '../components/BottomSheet.vue'
import SegTabs from '../components/SegTabs.vue'
import Icon from '../components/Icon.vue'
import { APP_NAME } from '../brand'
import { orderStatusText } from '../status'
import { emptyCopy, useOrderQuery } from '../orderQuery'
import { imgSrc, merchantCoverSrc, MERCHANT_COVER, onImgError } from '../img'
import { centsFromYuan, groupSkus, reportSummary, shopIsOpen, yuanDraft } from '../merchant'
import { CATEGORIES } from '../catalog'
import { loadMe, session, toast } from '../session'
import { api, download, upload } from '../api'

const router = useRouter()
const stats = ref({ series: [] })
const shop = ref({ items: [] })
const skus = ref([])
const range = ref('7d')
const board = ref('pending')
const cat = ref('')
const busy = ref(false)
const exporting = ref(false)
const shopOpen = ref(false)
const shopBusy = ref(false)
const coverInput = ref(null)
const shopForm = ref({ name: '', category: 'fresh', address: '', intro: '', phone: '' })
const scrolled = ref(false)
const loaded = ref(false)
const loadError = ref('')

function onScroll(e) {
  scrolled.value = e.target.scrollTop > 120
}

const ranges = [
  { key: '7d', label: '近七日' },
  { key: '30d', label: '近一月' },
  { key: '1y', label: '近一年' }
]
const boards = [
  { key: 'pending', label: '经营' },
  { key: 'goods', label: '商品' },
  { key: 'report', label: '报表' }
]

const pending = reactive(useOrderQuery(() => ({ status: 'MERCHANT_PENDING' })))
const waiting = reactive(useOrderQuery(() => ({ status: 'PAID' })))
const nowMs = ref(Date.now())
let tickTimer

function acceptRemainText(o) {
  const end = o.acceptDeadlineAt ? new Date(o.acceptDeadlineAt).getTime() : 0
  const sec = end
    ? Math.max(0, Math.floor((end - nowMs.value) / 1000))
    : (o.acceptRemainSeconds == null ? null : Math.max(0, Number(o.acceptRemainSeconds)))
  if (sec == null) return ''
  return `剩余 ${Math.floor(sec / 60)} 分 ${sec % 60} 秒（距 15 分钟超时）`
}

const online = computed(() => shopIsOpen({ ...session.me?.merchant, ...shop.value }))
const autoAccept = computed(() => !!(shop.value.autoAccept ?? session.me?.merchant?.autoAccept))
const shopName = computed(() => shop.value.shopName || session.me?.merchant?.shopName || '店铺经营')
const shopRating = computed(() => shop.value.ratingAvg || shop.value.rating || session.me?.merchant?.rating || 4.8)
const shopRatingCount = computed(() => shop.value.ratingCount ?? session.me?.merchant?.ratingCount ?? 0)
const coverSrc = computed(() => merchantCoverSrc({
  ...session.me?.merchant,
  ...shop.value,
  id: shop.value.id || session.me?.merchant?.id || session.me?.userId
}))
const chartTitle = computed(() => ranges.find((r) => r.key === range.value)?.label + '成交额')
const rangeGmv = computed(() => (stats.value.series || []).reduce((s, p) => s + (Number(p.gmvCents) || 0), 0))
const groups = computed(() => groupSkus(skus.value))
const activeSkus = computed(() => groups.value.find((g) => g.name === cat.value)?.skus || groups.value[0]?.skus || [])
const summaryText = computed(() => reportSummary(stats.value))

async function loadStats() {
  try {
    stats.value = (await api('/api/merchant/stats?range=' + range.value)).data || { series: [] }
  } catch (e) {
    stats.value = { series: [] }
    toast(e.message, 'err')
  }
}

async function loadSkus() {
  const res = await api('/api/merchants/me/skus').catch(() => ({ data: { items: [] } }))
  skus.value = res.data?.items || []
  if (!cat.value || !groups.value.some((g) => g.name === cat.value)) {
    cat.value = groups.value[0]?.name || ''
  }
}

async function refresh() {
  loadError.value = ''
  try {
    shop.value = (await api('/api/merchants/' + session.me.userId).catch(() => ({ data: { items: [] } }))).data || { items: [] }
    await Promise.all([pending.load(), waiting.load(), loadStats(), loadSkus()])
  } catch (e) {
    loadError.value = e.message || '经营数据加载失败'
    toast(e.message, 'err')
  } finally {
    loaded.value = true
  }
}

async function switchRange(key) {
  range.value = key
  await loadStats()
}

async function toggleShop() {
  busy.value = true
  try {
    const next = online.value ? 'OFFLINE' : 'ONLINE'
    const res = await api('/api/merchants/me/status', { method: 'PUT', body: { onlineStatus: next } })
    shop.value = { ...shop.value, ...res.data }
    await loadMe()
    toast(next === 'ONLINE' ? '已开始营业' : '已打烊')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

async function toggleAuto() {
  if (!online.value && !autoAccept.value) return toast('打烊后不能开启自动接单', 'err')
  busy.value = true
  try {
    const next = !autoAccept.value
    const res = await api('/api/merchants/me/settings', { method: 'PUT', body: { autoAccept: next } })
    shop.value = { ...shop.value, ...res.data }
    await loadMe()
    toast(next ? '已开启自动接单' : '已改为手动接单')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

async function accept(id) {
  if (!online.value) return toast('店铺已打烊，暂不可接新单', 'err')
  try {
    await api('/api/orders/' + id + '/merchant-accept', { method: 'POST' })
    toast('已接单，开始备餐')
    await refresh()
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function reject(id) {
  try {
    await api('/api/orders/' + id + '/merchant-reject', { method: 'POST', body: { reasonCode: 'STOCK', reasonText: '暂无法出餐' } })
    toast('已拒绝并退款')
    await refresh()
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function assign(id) {
  try {
    await api('/api/dispatch/assign/' + id, { method: 'POST' })
    toast('已派给附近骑手')
    await refresh()
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function patchSku(sku, body) {
  const res = await api('/api/merchants/me/skus/' + sku.id, { method: 'PUT', body })
  Object.assign(sku, res.data || {})
}

async function toggleSku(sku) {
  const next = sku.status === 'OFFLINE' ? 'ONLINE' : 'OFFLINE'
  try {
    await patchSku(sku, { status: next })
    toast(next === 'ONLINE' ? '已上架' : '已下架')
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function savePrice(sku, ev) {
  const cents = centsFromYuan(ev.target.value)
  if (!cents || cents === sku.priceCents) {
    ev.target.value = yuanDraft(sku.priceCents)
    return
  }
  try {
    await patchSku(sku, { priceCents: cents })
    toast('价格已更新，用户端立即生效')
  } catch (e) {
    ev.target.value = yuanDraft(sku.priceCents)
    toast(e.message, 'err')
  }
}

async function saveStock(sku, ev) {
  const n = Math.max(0, Math.round(Number(ev.target.value) || 0))
  if (n === Number(sku.stock)) return
  try {
    await patchSku(sku, { stock: n })
    toast('库存已更新')
  } catch (e) {
    ev.target.value = sku.stock
    toast(e.message, 'err')
  }
}

async function exportCsv() {
  exporting.value = true
  try {
    await download('/api/merchant/report.csv?range=' + range.value, 'shansuda-report-' + range.value + '.csv')
    toast('报表已导出')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    exporting.value = false
  }
}

function openShop() {
  shopForm.value = {
    name: shop.value.shopName || session.me?.merchant?.shopName || '',
    category: shop.value.category || session.me?.merchant?.category || 'fresh',
    address: shop.value.address || session.me?.merchant?.address || '',
    intro: shop.value.intro || '',
    phone: shop.value.phone || ''
  }
  shopOpen.value = true
}

async function saveShop() {
  shopBusy.value = true
  try {
    const res = await api('/api/merchants/me', {
      method: 'PUT',
      body: {
        name: shopForm.value.name,
        category: shopForm.value.category,
        address: shopForm.value.address,
        intro: shopForm.value.intro,
        phone: shopForm.value.phone
      }
    })
    shop.value = { ...shop.value, ...res.data }
    await loadMe()
    shopOpen.value = false
    toast('店铺资料已更新')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    shopBusy.value = false
  }
}

async function onCover(e) {
  const f = e.target.files?.[0]
  e.target.value = ''
  if (!f) return
  shopBusy.value = true
  try {
    const json = await upload('/api/merchants/me/cover', f)
    if (json.ok === false) throw new Error(json.message || '上传失败')
    shop.value = { ...shop.value, ...(json.data || {}) }
    await loadMe()
    toast('头图已更新，用户端立即生效')
  } catch (err) {
    toast(err.message, 'err')
  } finally {
    shopBusy.value = false
  }
}

onMounted(() => {
  tickTimer = setInterval(() => { nowMs.value = Date.now() }, 1000)
  refresh()
})
onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<template>
  <div class="phone page">
    <PageHeader overlay :solid="scrolled">
      <template #title>
        <span class="hh-brand">
          <img class="hh-logo" src="/logo.svg" :alt="APP_NAME" />
          <span class="hh-text">
            <b>店铺经营</b>
            <small>{{ session.me?.displayName || '商家' }} · {{ online ? '营业中' : '已打烊' }}</small>
          </span>
        </span>
      </template>
      <template #right><RoleBadge /></template>
    </PageHeader>
    <div class="phone-body" @scroll.passive="onScroll">
      <section class="hall-hero">
        <img
          class="hero-cover"
          :src="coverSrc"
          alt=""
          :class="{ 'is-off': !online }"
          :data-seed="'shop-' + (shop.id || session.me?.userId)"
          :data-category="shop.category || session.me?.merchant?.category || 'fresh'"
          :data-fallback-src="MERCHANT_COVER"
          @error="onImgError"
        />
        <div class="hero-copy">
          <span class="hero-kicker">{{ APP_NAME }} · 商家工作台</span>
          <h1 class="hero-title">{{ shopName }}</h1>
          <p class="hero-sub">{{ shopRating }} 分 · {{ shopRatingCount }} 评<span v-if="shop.promo"> · {{ shop.promo }}</span></p>
        </div>
        <div class="hero-stats">
          <div class="hero-stat">
            <small>今日订单</small>
            <b class="num">{{ stats.todayOrders || 0 }}</b>
          </div>
          <div class="hero-stat">
            <small>今日成交</small>
            <MoneyHover :cents="stats.todayGmvCents" label="今日成交">
              <template #default="{ text }"><b class="num">{{ text }}</b></template>
            </MoneyHover>
          </div>
          <div class="hero-stat">
            <small>待接单</small>
            <b class="num">{{ pending.total || 0 }}</b>
          </div>
        </div>
      </section>
      <div class="pad">
        <section class="duty" :class="{ 'is-on': online }" aria-label="营业状态">
          <div class="duty-top">
            <span class="duty-ico" aria-hidden="true"><Icon name="store" :size="22" :stroke="2.2" /></span>
            <div class="grow">
              <div class="duty-state">
                <span class="pulse-dot" :class="{ off: !online }"></span>
                {{ online ? '营业中' : '已打烊' }}
              </div>
              <p class="duty-sub">{{ autoAccept ? '自动接单已开，支付后直接进入待骑手' : '手动接单，超时 15 分钟会自动处理' }}</p>
            </div>
            <button class="btn duty-btn" :class="{ ghost: online }" type="button" :disabled="busy" @click="toggleShop">
              <span v-if="busy" class="spinner"></span>
              <Icon v-else :name="online ? 'moon' : 'zap'" :size="16" />
              {{ online ? '打烊' : '开始营业' }}
            </button>
          </div>
          <div class="duty-foot">
            <button class="btn btn-sm ghost" type="button" :disabled="busy || !online" @click="toggleAuto">
              {{ autoAccept ? '改为手动接单' : '开启自动接单' }}
            </button>
            <button class="btn btn-sm ghost" type="button" @click="openShop"><Icon name="edit" :size="14" />店铺资料</button>
          </div>
        </section>
        <div v-if="loadError" class="alert-strip" role="alert">
          <Icon name="alert" :size="18" />
          <div>
            <b>经营数据没有加载完整</b>
            <span>{{ loadError }}</span>
          </div>
          <button class="btn btn-sm btn-danger" type="button" @click="refresh">重试</button>
        </div>

        <div class="section">
          <SegTabs v-model="board" :options="boards" label="经营面板" />
        </div>

        <template v-if="board === 'pending'">
          <section class="card chart-card">
            <div class="card-head">
              <b class="card-title"><Icon name="chart" :size="16" class="ico" />{{ chartTitle }}</b>
              <MoneyHover :cents="rangeGmv" :label="chartTitle">
                <template #default="{ text }"><b class="num text-accent">{{ text }}</b></template>
              </MoneyHover>
            </div>
            <SegTabs :model-value="range" :options="ranges" label="统计区间" @change="switchRange" />
            <SalesChart :series="stats.series || []" :grain="stats.grain || (range === '1y' ? 'month' : 'day')" />
          </section>

          <section class="section">
            <div class="section-head">
              <h3 class="section-title"><Icon name="receipt" :size="18" class="text-accent" />待商家接单</h3>
            </div>
            <OrderToolbar
              v-model:q="pending.q"
              :size="pending.size"
              :page="pending.page"
              :pages="pending.pages"
              :has-next="pending.hasNext"
              :has-prev="pending.hasPrev"
              placeholder="搜索待接订单号、商品、地址"
              @size="pending.setSize"
              @prev="pending.prev"
              @next="pending.next"
            />
            <div v-if="!pending.loaded" class="stack-3" aria-busy="true">
              <div v-for="n in 2" :key="n" class="skel" style="height:112px"></div>
            </div>
            <article v-for="o in pending.items" :key="o.id" class="order-card" @click="router.push('/orders/' + o.id)">
              <div class="oc-head">
                <b class="num">#{{ o.id }}</b>
                <span class="pill tag-success">{{ orderStatusText(o.status) }}</span>
              </div>
              <div v-if="acceptRemainText(o)" class="oc-eta"><Icon name="timer" :size="13" />{{ acceptRemainText(o) }}</div>
              <div class="muted mt-1">{{ o.addressDetail }}</div>
              <div class="oc-actions" @click.stop>
                <button class="btn btn-sm ghost" type="button" @click="reject(o.id)">拒绝</button>
                <button class="btn btn-sm" type="button" :disabled="!online" :class="{ dim: !online }" @click="accept(o.id)">{{ online ? '接单备餐' : '已打烊' }}</button>
              </div>
            </article>
            <EmptyState v-if="pending.empty" title="暂无待接订单" :hint="!online ? '店铺已打烊，上线后可继续接单' : (autoAccept ? '已开自动接单，新支付单会直接进入待骑手' : emptyCopy('MERCHANT', 'pending'))" />
          </section>

          <section class="section">
            <div class="section-head">
              <h3 class="section-title"><Icon name="bike" :size="18" class="text-accent" />待骑手接单</h3>
            </div>
            <OrderToolbar
              v-model:q="waiting.q"
              :size="waiting.size"
              :page="waiting.page"
              :pages="waiting.pages"
              :has-next="waiting.hasNext"
              :has-prev="waiting.hasPrev"
              placeholder="搜索待抢订单号、商品、地址"
              @size="waiting.setSize"
              @prev="waiting.prev"
              @next="waiting.next"
            />
            <article v-for="o in waiting.items" :key="'paid-' + o.id" class="order-card" @click="router.push('/orders/' + o.id)">
              <div class="oc-head">
                <b class="num">#{{ o.id }}</b>
                <span class="pill">{{ orderStatusText(o.status) }}</span>
              </div>
              <div class="muted mt-2 row">
                <CookingStatus compact />
                <span>{{ o.addressDetail }}</span>
              </div>
              <button class="btn btn-block mt-3" type="button" @click.stop="assign(o.id)"><Icon name="route" :size="16" />派给附近骑手</button>
            </article>
            <EmptyState v-if="waiting.empty" title="暂无待抢订单" :hint="emptyCopy('MERCHANT', 'paid')" />
          </section>
        </template>

        <template v-else-if="board === 'goods'">
          <div class="section">
            <div class="chip-scroll" role="tablist" aria-label="商品分类">
              <button
                v-for="g in groups"
                :key="g.name"
                class="chip"
                type="button"
                :class="{ on: cat === g.name }"
                @click="cat = g.name"
              >{{ g.name }} {{ g.skus.length }}</button>
            </div>
            <div class="stack-2 mt-3">
              <article v-for="sku in activeSkus" :key="sku.id" class="sku-card">
                <img :src="imgSrc(sku.imageUrl, shop.category || 'fresh', 'sku-' + sku.id, 'dish')" alt="" :data-seed="'sku-' + sku.id" :data-category="shop.category || 'fresh'" @error="onImgError" />
                <div class="grow">
                  <div class="row-between">
                    <b class="ellipsis">{{ sku.name }}</b>
                    <span class="pill" :class="{ off: sku.status === 'OFFLINE' }">{{ sku.status === 'OFFLINE' ? '已下架' : '售卖中' }}</span>
                  </div>
                  <div class="sku-fields">
                    <label>售价
                      <span class="yen">¥</span>
                      <input :value="yuanDraft(sku.priceCents)" inputmode="decimal" aria-label="售价" @blur="savePrice(sku, $event)" />
                    </label>
                    <label>库存
                      <input :value="sku.stock" inputmode="numeric" :class="{ warn: sku.stockWarn || sku.stock < 10 }" aria-label="库存" @blur="saveStock(sku, $event)" />
                    </label>
                  </div>
                  <div class="row-between mt-2">
                    <span class="muted">已售 {{ sku.soldCount ?? sku.monthSales ?? 0 }}</span>
                    <button class="btn btn-xs ghost" type="button" @click="toggleSku(sku)">{{ sku.status === 'OFFLINE' ? '上架' : '下架' }}</button>
                  </div>
                </div>
              </article>
              <EmptyState v-if="!activeSkus.length" title="该分类暂无商品" hint="上架后用户端会立刻看到" />
            </div>
          </div>
        </template>

        <template v-else>
          <section class="card report-card">
            <p>{{ summaryText }}</p>
            <SegTabs class="mt-3" :model-value="range" :options="ranges" label="报表区间" @change="switchRange" />
            <div class="kpi-grid mt-3">
              <div class="kpi"><div class="kpi-label">进行中</div><div class="kpi-value">{{ stats.inProgress || 0 }}</div></div>
              <div class="kpi"><div class="kpi-label">已完成</div><div class="kpi-value">{{ stats.completed || 0 }}</div></div>
              <div class="kpi">
                <div class="kpi-label">退款</div>
                <MoneyHover :cents="stats.refundCents" label="退款">
                  <template #default="{ text }"><div class="kpi-value">{{ text }}</div></template>
                </MoneyHover>
              </div>
              <div class="kpi">
                <div class="kpi-label">客单价</div>
                <MoneyHover :cents="stats.avgPayCents" label="客单价">
                  <template #default="{ text }"><div class="kpi-value">{{ text }}</div></template>
                </MoneyHover>
              </div>
              <div class="kpi"><div class="kpi-label">退款率</div><div class="kpi-value">{{ Math.round((Number(stats.refundRate) || 0) * 1000) / 10 }}%</div></div>
            </div>
            <button class="btn btn-block mt-4" type="button" :disabled="exporting" @click="exportCsv">
              <span v-if="exporting" class="spinner"></span>
              <Icon v-else name="download" :size="16" />
              {{ exporting ? '导出中…' : '导出经营报表' }}
            </button>
          </section>
        </template>
      </div>
    </div>
    <TabBar />
    <input ref="coverInput" type="file" accept="image/*" hidden @change="onCover" />
    <BottomSheet :open="shopOpen" title="编辑店铺资料" subtitle="保存后用户端立即生效" @close="shopOpen = false">
      <label class="form-label" for="shop-name">店名</label>
      <input id="shop-name" v-model="shopForm.name" class="field" maxlength="64" />
      <label class="form-label" for="shop-cat">品类</label>
      <select id="shop-cat" v-model="shopForm.category" class="field">
        <option v-for="c in CATEGORIES" :key="c.key" :value="c.key">{{ c.name }}</option>
      </select>
      <label class="form-label" for="shop-addr">地址</label>
      <input id="shop-addr" v-model="shopForm.address" class="field" maxlength="255" />
      <label class="form-label" for="shop-phone">营业电话</label>
      <input id="shop-phone" v-model="shopForm.phone" class="field" maxlength="32" />
      <label class="form-label" for="shop-intro">简介</label>
      <textarea id="shop-intro" v-model="shopForm.intro" class="textarea" maxlength="512" rows="3"></textarea>
      <button class="btn ghost btn-block mt-3" type="button" :disabled="shopBusy" @click="coverInput?.click()">
        <Icon name="image" :size="16" />更换头图
      </button>
      <template #footer>
        <button class="btn ghost" type="button" @click="shopOpen = false">取消</button>
        <button class="btn grow" type="button" :disabled="shopBusy" @click="saveShop">
          <span v-if="shopBusy" class="spinner"></span>{{ shopBusy ? '保存中…' : '保存资料' }}
        </button>
      </template>
    </BottomSheet>
  </div>
</template>

<style scoped>
.hh-brand { display: flex; align-items: center; gap: 10px; min-width: 0; }
.hh-logo { width: 32px; height: 32px; border-radius: 10px; flex-shrink: 0; box-shadow: 0 4px 10px -4px rgba(0, 0, 0, 0.35); }
.hh-text { display: flex; flex-direction: column; min-width: 0; line-height: 1.2; }
.hh-text b { font-size: var(--fs-md); font-weight: 800; }
.hh-text small { font-size: var(--fs-xs); opacity: 0.72; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.hall-hero {
  position: relative;
  overflow: hidden;
  padding: calc(var(--header-h) + env(safe-area-inset-top, 0px) + 8px) var(--gutter) 72px;
  background: var(--hero-grad);
  color: #fff;
}
.hero-cover {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
  opacity: 0.28;
  mix-blend-mode: luminosity;
  -webkit-mask-image: linear-gradient(100deg, transparent 10%, #000 80%);
          mask-image: linear-gradient(100deg, transparent 10%, #000 80%);
}
.hero-cover.is-off { opacity: 0.12; filter: grayscale(1); }
.hero-copy, .hero-stats { position: relative; z-index: 1; }
.hero-kicker { font-size: var(--fs-xs); font-weight: 700; letter-spacing: 0.12em; color: rgba(255, 255, 255, 0.62); }
.hero-title { margin-top: 6px; font-size: var(--fs-2xl); font-weight: 800; letter-spacing: -0.02em; }
.hero-sub { margin-top: 4px; font-size: var(--fs-sm); color: rgba(255, 255, 255, 0.74); }
.hero-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 16px; }
.hero-stat {
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
}
.hero-stat small { display: block; font-size: var(--fs-2xs); color: rgba(255, 255, 255, 0.66); }
.hero-stat b { display: block; margin-top: 2px; font-size: var(--fs-lg); font-weight: 800; }

.duty {
  position: relative; z-index: 2;
  margin-top: -48px;
  padding: 16px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-3);
}
.duty.is-on { box-shadow: 0 0 0 1.5px color-mix(in srgb, var(--accent) 36%, transparent), var(--sh-3); }
.duty-top { display: flex; align-items: center; gap: 12px; }
.duty-ico {
  width: 46px; height: 46px;
  display: grid; place-items: center;
  border-radius: 16px;
  background: var(--surface-sunken);
  color: var(--text-3);
}
.duty.is-on .duty-ico { background: var(--accent-grad); color: #fff; box-shadow: var(--sh-accent); }
.duty-state { display: flex; align-items: center; gap: 8px; font-size: var(--fs-lg); font-weight: 800; }
.duty-sub { margin-top: 2px; font-size: var(--fs-xs); color: var(--text-2); }
.duty-btn { --btn-h: 44px; padding: 0 14px; flex-shrink: 0; }
.duty-foot { display: flex; gap: 8px; margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--line-strong); }

.alert-strip {
  display: flex; align-items: center; gap: 10px;
  margin-top: var(--s-3);
  padding: 12px;
  border-radius: var(--r-md);
  background: var(--danger-50);
  color: var(--danger-600);
}
.alert-strip b, .alert-strip span { display: block; }
.alert-strip span { font-size: var(--fs-xs); }

.chart-card { margin-top: var(--s-4); }
.order-card {
  margin-top: 10px;
  padding: 14px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-1);
  cursor: pointer;
}
.oc-head, .oc-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.oc-actions { justify-content: flex-end; margin-top: 12px; }
.oc-eta {
  display: inline-flex; align-items: center; gap: 4px;
  margin-top: 8px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--warning-50);
  color: var(--warning-600);
  font-size: var(--fs-xs); font-weight: 700;
}
.sku-card {
  display: flex; gap: 12px;
  padding: 12px;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
}
.sku-card img { width: 72px; height: 72px; border-radius: 14px; object-fit: cover; background: var(--ink-150); flex-shrink: 0; }
.sku-fields { display: flex; gap: 8px; margin-top: 8px; }
.sku-fields label {
  flex: 1;
  display: flex; align-items: center; gap: 4px;
  height: 34px; padding: 0 8px;
  border-radius: 10px;
  background: var(--surface-sunken);
  font-size: var(--fs-xs); color: var(--text-3); font-weight: 650;
}
.sku-fields input {
  width: 100%;
  border: 0; background: transparent;
  font-family: var(--font-num);
  font-size: var(--fs-sm); font-weight: 750; color: var(--text);
}
.sku-fields input.warn { color: var(--danger-600); }
.yen { color: var(--text-2); }
.report-card { margin-top: var(--s-4); }
.report-card > p { font-size: var(--fs-sm); color: var(--text-2); line-height: 1.6; }
</style>
