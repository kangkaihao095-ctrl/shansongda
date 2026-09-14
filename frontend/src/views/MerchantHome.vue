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
const chartTitle = computed(() => ranges.find((r) => r.key === range.value)?.label + ' GMV')
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
  try {
    shop.value = (await api('/api/merchants/' + session.me.userId).catch(() => ({ data: { items: [] } }))).data || { items: [] }
    await Promise.all([pending.load(), waiting.load(), loadStats(), loadSkus()])
  } catch (e) {
    toast(e.message, 'err')
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
    toast('已按路径代价指派骑手')
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
  <div class="phone page theme-merchant" :class="{ 'is-closed': !online }">
    <header class="frost pad">
      <div class="row" style="justify-content:space-between;align-items:flex-start">
        <div>
          <b class="page-title" style="font-size:18px">店铺经营</b>
          <div class="slogan">{{ session.me?.displayName }} · {{ shop.address || session.me?.merchant?.address || '经营台' }}</div>
        </div>
        <RoleBadge />
      </div>
    </header>
    <div class="phone-body">
      <div class="hall-hero">
        <img
          class="hero-cover hall-cover"
          :src="coverSrc"
          alt=""
          :class="{ 'is-off': !online }"
          :data-seed="'shop-' + (shop.id || session.me?.userId)"
          :data-category="shop.category || session.me?.merchant?.category || 'fresh'"
          :data-fallback-src="MERCHANT_COVER"
          @error="onImgError"
        />
        <div class="hall-hero-meta">
          <b>{{ shopName }}</b>
          <span>{{ shopRating }} 分 · {{ shopRatingCount }} 评{{ shop.promo ? ' · ' + shop.promo : '' }}</span>
        </div>
      </div>
      <div class="pad">
        <div class="duty-compact" :class="online ? 'duty-on' : 'duty-off'">
          <div>
            <div class="duty-badge">
              <span class="pulse-dot" :class="{ off: !online }"></span>
              {{ online ? '营业中' : '已打烊' }}
            </div>
            <div class="muted" style="margin-top:4px">{{ autoAccept ? '自动接单' : '手动接单' }}</div>
          </div>
          <div class="row" style="flex-wrap:nowrap">
            <button class="btn ghost" type="button" :disabled="busy || !online" @click="toggleAuto">{{ autoAccept ? '切手动' : '开自动' }}</button>
            <button class="btn" :class="{ ghost: online }" :disabled="busy" @click="toggleShop">
              {{ online ? '打烊' : '营业' }}
            </button>
          </div>
        </div>
        <div class="merchant-ops" :class="{ 'is-off': !online }">
        <div class="board-tabs">
          <button v-for="b in boards" :key="b.key" type="button" :class="{ on: board === b.key }" @click="board = b.key">{{ b.label }}</button>
        </div>

        <template v-if="board === 'pending'">
          <div class="overview-grid">
            <div class="overview-cell stat-card">
              <div class="muted">今日订单</div>
              <div class="num">{{ stats.todayOrders || 0 }}</div>
            </div>
            <div class="overview-cell stat-card">
              <div class="muted">今日成交</div>
              <MoneyHover :cents="stats.todayGmvCents" label="今日成交">
                <template #default="{ text }">
                  <div class="num">{{ text }}</div>
                </template>
              </MoneyHover>
            </div>
          </div>
          <div class="chart-card">
            <div class="row" style="justify-content:space-between;margin-bottom:8px">
              <b>{{ chartTitle }}</b>
              <MoneyHover :cents="rangeGmv" :label="chartTitle">
                <template #default="{ text }"><span class="num" style="font-weight:800;color:#0F6E64">{{ text }}</span></template>
              </MoneyHover>
            </div>
            <div class="seg">
              <button v-for="r in ranges" :key="r.key" type="button" :class="{ on: range === r.key }" @click="switchRange(r.key)">{{ r.label }}</button>
            </div>
            <SalesChart :series="stats.series || []" :grain="stats.grain || (range === '1y' ? 'month' : 'day')" />
          </div>
          <h3 class="section-title">待商家接单</h3>
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
          <article v-for="o in pending.items" :key="o.id" class="order-card" @click="router.push('/orders/' + o.id)">
            <div class="order-card-head">
              <b>#{{ o.id }}</b>
              <span class="status-pill live">{{ orderStatusText(o.status) }}</span>
            </div>
            <div v-if="acceptRemainText(o)" class="order-eta">{{ acceptRemainText(o) }}</div>
            <div class="muted">{{ o.addressDetail }}</div>
            <div class="row" style="margin-top:8px" @click.stop>
              <button class="btn" :disabled="!online" :class="{ dim: !online }" @click="accept(o.id)">{{ online ? '接单备餐' : '已打烊' }}</button>
              <button class="btn ghost" @click="reject(o.id)">拒绝</button>
            </div>
          </article>
          <EmptyState v-if="pending.empty" title="暂无待接订单" :hint="!online ? '店铺已打烊，上线后可继续接单' : (autoAccept ? '已开自动接单，新支付单会直接进入待骑手' : emptyCopy('MERCHANT', 'pending'))" />
          <h3 class="section-title">待骑手接单</h3>
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
            <div class="order-card-head">
              <b>#{{ o.id }}</b>
              <span class="status-pill">{{ orderStatusText(o.status) }}</span>
            </div>
            <div class="muted" style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
              <CookingStatus compact />
              <span>{{ o.addressDetail }}</span>
            </div>
            <button class="btn" style="width:100%;margin-top:8px" @click.stop="assign(o.id)">自动指派骑手</button>
          </article>
          <EmptyState v-if="waiting.empty" title="暂无待抢订单" :hint="emptyCopy('MERCHANT', 'paid')" />
        </template>

        <template v-else-if="board === 'goods'">
          <div class="row" style="justify-content:space-between;margin-bottom:10px">
            <b>商品分类</b>
            <button class="btn ghost" type="button" @click="openShop">店铺资料</button>
          </div>
          <div class="sku-board">
            <nav class="sku-cats">
              <button
                v-for="g in groups"
                :key="g.name"
                type="button"
                :class="{ on: cat === g.name }"
                @click="cat = g.name"
              >
                {{ g.name }}
                <span class="muted">{{ g.skus.length }}</span>
              </button>
            </nav>
            <div class="sku-pane">
              <article v-for="sku in activeSkus" :key="sku.id" class="sku-manage">
                <img :src="imgSrc(sku.imageUrl, shop.category || 'fresh', 'sku-' + sku.id, 'dish')" alt="" :data-seed="'sku-' + sku.id" :data-category="shop.category || 'fresh'" @error="onImgError" />
                <div class="sku-manage-body">
                  <div class="row" style="justify-content:space-between;gap:6px">
                    <b>{{ sku.name }}</b>
                    <span class="pill" :class="{ off: sku.status === 'OFFLINE' }">{{ sku.status === 'OFFLINE' ? '已下架' : '售卖中' }}</span>
                  </div>
                  <div class="sku-meta">
                    <label>¥<input :value="yuanDraft(sku.priceCents)" inputmode="decimal" @blur="savePrice(sku, $event)" /></label>
                    <label>库存 <input :value="sku.stock" inputmode="numeric" :class="{ warn: sku.stockWarn || sku.stock < 10 }" @blur="saveStock(sku, $event)" /></label>
                    <span>已售 {{ sku.soldCount ?? sku.monthSales ?? 0 }}</span>
                  </div>
                  <button class="btn ghost sku-toggle" @click="toggleSku(sku)">{{ sku.status === 'OFFLINE' ? '上架' : '下架' }}</button>
                </div>
              </article>
              <p v-if="!activeSkus.length" class="muted" style="padding:16px">该分类暂无商品</p>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="report-board">
            <p class="muted">{{ summaryText }}</p>
            <div class="seg" style="margin:12px 0">
              <button v-for="r in ranges" :key="r.key" type="button" :class="{ on: range === r.key }" @click="switchRange(r.key)">{{ r.label }}</button>
            </div>
            <div class="report-kpis">
              <div><span class="muted">进行中</span><b>{{ stats.inProgress || 0 }}</b></div>
              <div><span class="muted">已完成</span><b>{{ stats.completed || 0 }}</b></div>
              <div>
                <span class="muted">退款</span>
                <MoneyHover :cents="stats.refundCents" label="退款">
                  <template #default="{ text }"><b>{{ text }}</b></template>
                </MoneyHover>
              </div>
              <div>
                <span class="muted">客单价</span>
                <MoneyHover :cents="stats.avgPayCents" label="客单价">
                  <template #default="{ text }"><b>{{ text }}</b></template>
                </MoneyHover>
              </div>
              <div><span class="muted">退款率</span><b>{{ Math.round((Number(stats.refundRate) || 0) * 1000) / 10 }}%</b></div>
            </div>
            <button class="btn" style="width:100%;margin-top:12px" :disabled="exporting" @click="exportCsv">导出报表</button>
          </div>
        </template>
        </div>
      </div>
    </div>
    <TabBar />
    <input ref="coverInput" type="file" accept="image/*" hidden @change="onCover" />
    <Teleport to="body">
      <div v-if="shopOpen" class="coupon-mask" @click.self="shopOpen = false">
        <div class="loc-sheet profile-sheet" @click.stop>
          <div class="sheet-handle"></div>
          <b>编辑店铺资料</b>
          <label class="shop-field">店名
            <input v-model="shopForm.name" maxlength="64" />
          </label>
          <label class="shop-field">品类
            <select v-model="shopForm.category">
              <option v-for="c in CATEGORIES" :key="c.key" :value="c.key">{{ c.name }}</option>
            </select>
          </label>
          <label class="shop-field">地址
            <input v-model="shopForm.address" maxlength="255" />
          </label>
          <label class="shop-field">营业电话
            <input v-model="shopForm.phone" maxlength="32" />
          </label>
          <label class="shop-field">简介
            <textarea v-model="shopForm.intro" maxlength="512" rows="3"></textarea>
          </label>
          <button class="profile-action" type="button" :disabled="shopBusy" @click="coverInput?.click()">更换头图</button>
          <div class="row" style="margin-top:12px">
            <button class="btn" type="button" :disabled="shopBusy" @click="saveShop">保存</button>
            <button class="btn ghost" type="button" @click="shopOpen = false">取消</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
