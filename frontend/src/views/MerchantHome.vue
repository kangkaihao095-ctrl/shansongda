<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import SalesChart from '../components/SalesChart.vue'
import RoleBadge from '../components/RoleBadge.vue'
import OrderToolbar from '../components/OrderToolbar.vue'
import EmptyState from '../components/EmptyState.vue'
import MoneyHover from '../components/MoneyHover.vue'
import CookingStatus from '../components/CookingStatus.vue'
import { orderStatusText } from '../status'
import { api, download } from '../api'
import { emptyCopy, useOrderQuery } from '../orderQuery'
import { imgSrc, onImgError } from '../img'
import { centsFromYuan, groupSkus, reportSummary, yuanDraft } from '../merchant'
import { loadMe, session, toast } from '../session'

const router = useRouter()
const stats = ref({ series: [] })
const shop = ref({ items: [] })
const skus = ref([])
const range = ref('7d')
const board = ref('pending')
const cat = ref('')
const busy = ref(false)
const exporting = ref(false)

const ranges = [
  { key: '7d', label: '近七日' },
  { key: '30d', label: '近一月' },
  { key: '1y', label: '近一年' }
]
const boards = [
  { key: 'pending', label: '待商家接单' },
  { key: 'goods', label: '商品' },
  { key: 'report', label: '报表' }
]

const pending = reactive(useOrderQuery(() => ({ status: 'MERCHANT_PENDING' })))
const waiting = reactive(useOrderQuery(() => ({ status: 'PAID' })))

const online = computed(() => (shop.value.onlineStatus || session.me?.merchant?.onlineStatus) !== 'OFFLINE')
const autoAccept = computed(() => !!(shop.value.autoAccept ?? session.me?.merchant?.autoAccept))
const chartTitle = computed(() => ranges.find((r) => r.key === range.value)?.label + ' GMV')
const rangeGmv = computed(() => (stats.value.series || []).reduce((s, p) => s + (Number(p.gmvCents) || 0), 0))
const groups = computed(() => groupSkus(skus.value))
const activeSkus = computed(() => groups.value.find((g) => g.name === cat.value)?.skus || groups.value[0]?.skus || [])
const summaryText = computed(() => reportSummary(stats.value))

async function loadStats() {
  stats.value = (await api('/api/merchant/stats?range=' + range.value)).data || { series: [] }
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
  try {
    await loadStats()
  } catch (e) {
    toast(e.message, 'err')
  }
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

onMounted(refresh)
</script>

<template>
  <div class="phone page theme-merchant">
    <header class="frost pad">
      <div class="brand-row">
        <img class="brand-mark" src="/logo.svg" alt="" />
        <div style="flex:1">
          <div class="row" style="justify-content:space-between">
            <b>{{ session.me?.merchant?.shopName || shop.shopName || '店铺经营' }}</b>
            <RoleBadge />
          </div>
          <div class="slogan">{{ session.me?.displayName }} · 商家中心</div>
        </div>
      </div>
      <div class="muted" style="margin-top:6px">{{ session.me?.merchant?.address || shop.address }}</div>
    </header>
    <div class="phone-body">
      <img
        class="hero-cover"
        :src="imgSrc(shop.coverUrl, shop.category || 'fresh', 'shop-' + shop.id, 'shop')"
        alt=""
        :data-seed="'shop-' + shop.id"
        @error="onImgError"
        :class="{ 'is-off': !online }"
      />
      <div class="pad">
        <div class="duty-card" :class="online ? 'duty-on' : 'duty-off'" style="margin-bottom:12px">
          <div class="row" style="justify-content:space-between;align-items:flex-start">
            <div>
              <div class="duty-badge">
                <span class="pulse-dot" :class="{ off: !online }"></span>
                {{ online ? '营业中' : '已打烊' }}
              </div>
              <div class="muted" style="margin-top:6px">{{ online ? '用户可向本店下单' : '打烊后用户端不可向本店下单' }}</div>
            </div>
            <button class="btn" :class="{ ghost: online }" :disabled="busy" @click="toggleShop">
              {{ online ? '打烊' : '开始营业' }}
            </button>
          </div>
          <div class="accept-switch" :class="{ on: autoAccept }">
            <div>
              <b>{{ autoAccept ? '自动接单' : '手动接单' }}</b>
              <div class="muted">{{ autoAccept ? '用户支付后自动接单，进入待骑手接单' : '需在待接单里点接单或拒绝' }}</div>
            </div>
            <button class="btn" :class="{ ghost: !autoAccept }" :disabled="busy" @click="toggleAuto">
              {{ autoAccept ? '切手动' : '开自动' }}
            </button>
          </div>
        </div>
        <div class="row" style="margin-bottom:10px">
          <div class="card stat-card" style="flex:1">
            <div class="muted">今日订单</div>
            <div class="num" style="font-size:22px;font-weight:800">{{ stats.todayOrders || 0 }}</div>
          </div>
          <div class="card stat-card" style="flex:1">
            <div class="muted">今日成交</div>
            <MoneyHover :cents="stats.todayGmvCents" label="今日成交">
              <template #default="{ text }">
                <div class="num" style="font-size:18px;font-weight:800">{{ text }}</div>
              </template>
            </MoneyHover>
          </div>
        </div>
        <div class="board-tabs">
          <button v-for="b in boards" :key="b.key" type="button" :class="{ on: board === b.key }" @click="board = b.key">{{ b.label }}</button>
        </div>

        <template v-if="board === 'pending'">
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
          <article v-for="o in pending.items" :key="o.id" class="card" style="margin-bottom:10px" @click="router.push('/orders/' + o.id)">
            <div class="row" style="justify-content:space-between">
              <b>#{{ o.id }}</b>
              <span class="pill">{{ orderStatusText(o.status) }}</span>
            </div>
            <div class="muted">{{ o.addressDetail }}</div>
            <div class="row" style="margin-top:8px" @click.stop>
              <button class="btn" @click="accept(o.id)">接单备餐</button>
              <button class="btn ghost" @click="reject(o.id)">拒绝</button>
            </div>
          </article>
          <EmptyState v-if="pending.empty" title="暂无待接订单" :hint="autoAccept ? '已开自动接单，新支付单会直接进入待骑手' : emptyCopy('MERCHANT', 'pending')" />
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
          <article v-for="o in waiting.items" :key="'paid-' + o.id" class="card" style="margin-bottom:10px" @click="router.push('/orders/' + o.id)">
            <div class="row" style="justify-content:space-between">
              <b>#{{ o.id }}</b>
              <span class="pill">{{ orderStatusText(o.status) }}</span>
            </div>
            <div class="muted" style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
              <CookingStatus compact />
              <span>{{ o.addressDetail }}</span>
            </div>
            <button class="btn" style="margin-top:8px" @click.stop="assign(o.id)">自动指派骑手</button>
          </article>
          <EmptyState v-if="waiting.empty" title="暂无待抢订单" :hint="emptyCopy('MERCHANT', 'paid')" />
          <p class="muted" style="margin-top:8px">全部店铺订单请到「订单」Tab，支持搜索与 5/10/20 分页。</p>
        </template>

        <template v-else-if="board === 'goods'">
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
                    <label>库存 <input :value="sku.stock" inputmode="numeric" @blur="saveStock(sku, $event)" /></label>
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
          <div class="row" style="margin-bottom:12px">
            <div class="card stat-card" style="flex:1"><div class="muted">进行中</div><b>{{ stats.inProgress || 0 }}</b></div>
            <div class="card stat-card" style="flex:1"><div class="muted">已完成</div><b>{{ stats.completed || 0 }}</b></div>
            <div class="card stat-card" style="flex:1">
              <div class="muted">退款金额</div>
              <MoneyHover :cents="stats.refundCents" label="退款">
                <template #default="{ text }"><b>{{ text }}</b></template>
              </MoneyHover>
            </div>
          </div>
          <div class="card summary-card" style="margin-bottom:12px">
            <b>近期情况</b>
            <p>{{ summaryText }}</p>
          </div>
          <div class="card stat-card" style="margin-bottom:14px">
            <div class="row" style="justify-content:space-between;margin-bottom:10px">
              <b>{{ chartTitle }}</b>
              <MoneyHover :cents="rangeGmv" :label="chartTitle">
                <template #default="{ text }"><span class="num" style="font-weight:800">{{ text }}</span></template>
              </MoneyHover>
            </div>
            <div class="seg">
              <button v-for="r in ranges" :key="r.key" type="button" :class="{ on: range === r.key }" @click="switchRange(r.key)">{{ r.label }}</button>
            </div>
            <SalesChart :series="stats.series || []" :grain="stats.grain || (range === '1y' ? 'month' : 'day')" />
            <button class="btn" style="width:100%;margin-top:12px" :disabled="exporting" @click="exportCsv">导出报表</button>
          </div>
        </template>
      </div>
    </div>
    <TabBar />
  </div>
</template>
