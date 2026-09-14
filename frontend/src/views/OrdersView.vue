<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import RoleBadge from '../components/RoleBadge.vue'
import OrderToolbar from '../components/OrderToolbar.vue'
import EmptyState from '../components/EmptyState.vue'
import { yuan } from '../brand'
import {
  canMerchantAccept,
  canUserPay,
  etaLabel,
  orderStatusHeadline,
  riderStatusHint,
  snapshotOf
} from '../status'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { emptyCopy, useOrderQuery } from '../orderQuery'
import { fillFromSnapshot } from '../cart'
import { shopIsOpen } from '../merchant'
import { session, toast } from '../session'

const router = useRouter()
const filter = ref('')
const role = computed(() => session.me?.role)
const isRider = computed(() => role.value === 'RIDER')
const isMerchant = computed(() => role.value === 'MERCHANT')
const merchantOnline = computed(() => shopIsOpen(session.me?.merchant))
const {
  q, size, page, items, pages, hasNext, hasPrev, loading, empty,
  load, setSize, prev, next
} = useOrderQuery(() => ({
  status: isRider.value ? undefined : (filter.value || undefined),
  scene: isRider.value ? 'done' : undefined
}))

const title = computed(() => {
  if (isRider.value) return '已完成任务'
  if (role.value === 'MERCHANT') return '商家订单'
  return '我的外卖订单'
})
const placeholder = computed(() => isRider.value
  ? '搜索已完成订单号、商家、商品、地址'
  : '搜索订单号、状态、店铺、商品、地址')
const tabs = [
  { key: '', label: '全部' },
  { key: 'CREATED', label: '待支付' },
  { key: 'MERCHANT_PENDING', label: '待商家接单' },
  { key: 'PAID', label: '备餐中' },
  { key: 'DELIVERING', label: '配送中' },
  { key: 'COMPLETED', label: '已完成' }
]

async function attachEta(rows) {
  const list = Array.isArray(rows) ? rows : []
  const need = list.filter((o) => {
    if (!['PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING'].includes(o.status)) return false
    return o.etaMs == null || o.etaMs === ''
  })
  if (!need.length) return
  await Promise.all(need.map(async (o) => {
    try {
      const t = (await api(`/api/orders/${o.id}/track`)).data
      o.etaMs = t.etaMs
    } catch { /* 列表已有 etaMs 或规划失败则保持 */ }
  }))
}

async function reload() {
  await load()
  await attachEta(items.value)
}

async function act(path, id) {
  if (path === 'merchant-accept' && !merchantOnline.value) return toast('店铺已打烊，暂不可接新单', 'err')
  try {
    await api(`/api/orders/${id}/${path}`, { method: 'POST' })
    toast('已更新')
    await reload()
  } catch (e) { toast(e.message, 'err') }
}

function reorder(o) {
  const snap = snapshotOf(o)
  const items = snap.items || []
  if (!items.length) return toast('该订单没有可复用的商品快照', 'err')
  fillFromSnapshot(o.merchantId, snap.shopName, o.coverUrl || snap.coverUrl, items)
  toast('已按上次商品加入购物车')
  router.push('/checkout')
}

function cover(o) {
  return imgSrc(o.coverUrl || snapshotOf(o).items?.[0]?.imageUrl, 'food', 'order-' + o.id, 'shop')
}

watch(filter, () => { page.value = 1; reload() })
onMounted(reload)
</script>

<template>
  <div class="phone page" :class="{ 'theme-rider': isRider, 'theme-merchant': isMerchant, 'is-closed': isMerchant && !merchantOnline }">
    <header class="frost pad row" style="justify-content:space-between">
      <b class="page-title" style="font-size:18px">{{ title }}</b>
      <RoleBadge />
    </header>
    <div class="phone-body pad" :class="{ 'merchant-ops': isMerchant, 'is-off': isMerchant && !merchantOnline }">
      <div v-if="!isRider" class="seg" style="margin-bottom:12px;flex-wrap:wrap">
        <button v-for="t in tabs" :key="t.key" type="button" :class="{ on: filter === t.key }" @click="filter = t.key">{{ t.label }}</button>
      </div>
      <OrderToolbar
        v-model:q="q"
        :size="size"
        :page="page"
        :pages="pages"
        :has-next="hasNext"
        :has-prev="hasPrev"
        :placeholder="placeholder"
        @size="n => setSize(n).then(() => attachEta(items))"
        @prev="prev().then(() => attachEta(items))"
        @next="next().then(() => attachEta(items))"
      />
      <div v-if="loading" class="skel" style="height:90px"></div>
      <article v-for="o in items" :key="o.id" class="order-card" @click="router.push('/orders/' + o.id)">
        <div class="order-card-head">
          <b>{{ snapshotOf(o).shopName || '闪送达商家' }}</b>
          <span class="status-pill" :class="{ live: ['PAID','ACCEPTED','ARRIVED','DELIVERING'].includes(o.status), done: o.status === 'COMPLETED' }">{{ orderStatusHeadline(o.status, o.etaMs, role) }}</span>
        </div>
        <div class="order-card-body">
          <div class="order-thumbs">
            <img :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" class="order-thumb" />
          </div>
          <div class="order-card-copy">
            <div v-if="isRider && riderStatusHint(o.status)" class="muted">{{ riderStatusHint(o.status) }}</div>
            <div class="muted">{{ (snapshotOf(o).items || []).map((it) => it.name).filter(Boolean).slice(0, 2).join('、') || o.addressDetail }}</div>
            <div v-if="['PAID','ACCEPTED','ARRIVED','DELIVERING'].includes(o.status)" class="order-eta">{{ etaLabel(o.etaMs, o.status) }}</div>
          </div>
          <div class="order-amt num">{{ yuan((o.payAmountCents) || (o.goodsAmountCents + o.freightCents)) }}</div>
        </div>
        <div v-if="!isRider" class="row" style="margin-top:10px" @click.stop>
          <button v-if="canUserPay(role, o.status)" class="btn" @click="router.push('/orders/' + o.id + '/pay')">支付</button>
          <button v-if="canMerchantAccept(role, o.status)" class="btn" :disabled="!merchantOnline" :class="{ dim: !merchantOnline }" @click="act('merchant-accept', o.id)">{{ merchantOnline ? '接单' : '已打烊' }}</button>
          <button v-if="canMerchantAccept(role, o.status)" class="btn ghost" @click="api('/api/orders/'+o.id+'/merchant-reject',{method:'POST',body:{reasonCode:'STOCK',reasonText:'暂无法出餐'}}).then(reload).catch(e=>toast(e.message,'err'))">拒绝</button>
          <button v-if="role === 'USER' && o.status === 'COMPLETED' && o.canReorder !== false" class="btn ghost" @click="reorder(o)">再来一单</button>
          <button v-if="role === 'USER' && o.status === 'COMPLETED' && !o.reviewed" class="btn" @click="router.push('/orders/' + o.id + '/review')">评价</button>
          <button v-if="role === 'USER' && o.status === 'COMPLETED' && o.reviewed" class="btn ghost" @click="router.push('/shops/' + o.merchantId + (o.reviewId ? ('?reviewId=' + o.reviewId) : ''))">查看评价</button>
        </div>
      </article>
      <EmptyState v-if="empty" :title="isRider ? '还没有已完成配送' : '暂无订单'" :hint="emptyCopy(role, isRider ? 'done' : 'list')" />
    </div>
    <TabBar />
  </div>
</template>
