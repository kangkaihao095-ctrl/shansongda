<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import RoleBadge from '../components/RoleBadge.vue'
import OrderToolbar from '../components/OrderToolbar.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import Icon from '../components/Icon.vue'
import { yuan } from '../brand'
import {
  canMerchantAccept,
  canUserPay,
  etaLabel,
  formatDateTime,
  needsListTrack,
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
const acting = ref('')
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
  return '我的订单'
})
const subtitle = computed(() => {
  if (isRider.value) return '已完成的配送都在这里'
  if (isMerchant.value) return merchantOnline.value ? '营业中 · 新订单会马上出现' : '已打烊 · 只能看历史订单'
  return '下单到送达，进度随时可看'
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
const LIVE = ['PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING']

function tone(status) {
  if (LIVE.includes(status)) return 'tag-success'
  if (status === 'CREATED' || status === 'MERCHANT_PENDING' || status === 'CANCELLING') return 'tag-warn'
  if (status === 'REFUNDING' || status === 'REFUND_REJECTED') return 'tag-danger'
  if (status === 'COMPLETED') return ''
  return 'tag-neutral'
}

async function attachEta(rows) {
  const list = Array.isArray(rows) ? rows : []
  const need = list.filter(needsListTrack)
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
  acting.value = id + path
  try {
    await api(`/api/orders/${id}/${path}`, { method: 'POST' })
    toast('已更新')
    await reload()
  } catch (e) { toast(e.message, 'err') } finally { acting.value = '' }
}

async function reject(id) {
  acting.value = id + 'reject'
  try {
    await api('/api/orders/' + id + '/merchant-reject', { method: 'POST', body: { reasonCode: 'STOCK', reasonText: '暂无法出餐' } })
    await reload()
  } catch (e) { toast(e.message, 'err') } finally { acting.value = '' }
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

function goods(o) {
  const list = snapshotOf(o).items || []
  const names = list.map((it) => it.name).filter(Boolean)
  if (!names.length) return o.addressDetail
  return names.slice(0, 2).join('、') + (names.length > 2 ? ` 等 ${list.reduce((s, it) => s + (it.qty || 1), 0)} 件` : '')
}

function hasActions(o) {
  if (isRider.value) return false
  return canUserPay(role.value, o.status) || canMerchantAccept(role.value, o.status) || (role.value === 'USER' && o.status === 'COMPLETED')
}

watch(filter, () => { page.value = 1; reload() })
onMounted(reload)
</script>

<template>
  <div class="phone page">
    <PageHeader :title="title" :subtitle="subtitle">
      <template #right><RoleBadge /></template>
    </PageHeader>
    <div class="phone-body">
      <div class="pad orders-top">
        <div v-if="isMerchant && !merchantOnline" class="notice"><Icon name="moon" :size="16" />店铺已打烊，暂不可接新单，营业后恢复</div>
        <div v-if="!isRider" class="chip-scroll filter-tabs" role="tablist" aria-label="订单状态筛选">
          <button
            v-for="t in tabs"
            :key="t.key"
            class="chip"
            :class="{ on: filter === t.key }"
            type="button"
            role="tab"
            :aria-selected="filter === t.key"
            @click="filter = t.key"
          >{{ t.label }}</button>
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
      </div>

      <div class="pad" :class="{ 'is-dim': isMerchant && !merchantOnline }">
        <div v-if="loading && !items.length" aria-busy="true">
          <div v-for="n in 3" :key="n" class="skel-card">
            <div class="row-between"><div class="skel skel-line" style="width:40%"></div><div class="skel skel-line" style="width:20%"></div></div>
            <div class="row mt-3"><div class="skel" style="width:60px;height:60px"></div><div class="grow"><div class="skel skel-line" style="width:70%"></div><div class="skel skel-line mt-2" style="width:40%"></div></div></div>
          </div>
        </div>

        <transition-group v-else name="list" tag="div" class="order-list" :class="{ refreshing: loading }">
          <article
            v-for="o in items"
            :key="o.id"
            class="order-card"
            tabindex="0"
            role="link"
            :aria-label="(snapshotOf(o).shopName || '订单') + ' ' + orderStatusHeadline(o.status, o.etaMs, role)"
            @click="router.push('/orders/' + o.id)"
            @keydown.enter="router.push('/orders/' + o.id)"
          >
            <header class="oc-head">
              <span class="oc-shop"><Icon name="store" :size="15" /><b class="ellipsis">{{ snapshotOf(o).shopName || '闪送达商家' }}</b><Icon name="chevron" :size="14" class="subtle" /></span>
              <span class="tag" :class="tone(o.status)"><i v-if="LIVE.includes(o.status)" class="dot oc-live"></i>{{ orderStatusHeadline(o.status, o.etaMs, role) }}</span>
            </header>
            <div class="oc-body">
              <img :src="cover(o)" alt="" :data-seed="'order-' + o.id" class="oc-thumb" loading="lazy" @error="onImgError" />
              <div class="grow">
                <div v-if="isRider && riderStatusHint(o.status)" class="oc-hint">{{ riderStatusHint(o.status) }}</div>
                <div class="oc-goods ellipsis">{{ goods(o) }}</div>
                <div class="oc-time num">{{ formatDateTime(o.createdAt) || ('#' + o.id) }}</div>
                <div v-if="LIVE.includes(o.status)" class="oc-eta"><Icon name="clock" :size="13" />{{ etaLabel(o.etaMs, o.status) }}</div>
              </div>
              <div class="oc-amt">
                <b class="num">{{ yuan((o.payAmountCents) || (o.goodsAmountCents + o.freightCents)) }}</b>
                <small>{{ o.payAmountCents ? '实付' : '应付' }}</small>
              </div>
            </div>
            <footer v-if="hasActions(o)" class="oc-actions" @click.stop>
              <button v-if="canUserPay(role, o.status)" class="btn btn-sm" type="button" @click="router.push('/orders/' + o.id + '/pay')">去支付</button>
              <button v-if="canMerchantAccept(role, o.status)" class="btn btn-sm ghost" type="button" :disabled="acting === o.id + 'reject'" @click="reject(o.id)">拒绝</button>
              <button v-if="canMerchantAccept(role, o.status)" class="btn btn-sm" :disabled="!merchantOnline || acting === o.id + 'merchant-accept'" :class="{ dim: !merchantOnline }" type="button" @click="act('merchant-accept', o.id)">{{ merchantOnline ? '接单' : '已打烊' }}</button>
              <button v-if="role === 'USER' && o.status === 'COMPLETED' && o.canReorder !== false" class="btn btn-sm ghost" type="button" @click="reorder(o)"><Icon name="refresh" :size="14" />再来一单</button>
              <button v-if="role === 'USER' && o.status === 'COMPLETED' && !o.reviewed" class="btn btn-sm" type="button" @click="router.push('/orders/' + o.id + '/review')"><Icon name="star" :size="14" />评价</button>
              <button v-if="role === 'USER' && o.status === 'COMPLETED' && o.reviewed" class="btn btn-sm ghost" type="button" @click="router.push('/shops/' + o.merchantId + (o.reviewId ? ('?reviewId=' + o.reviewId) : ''))">查看评价</button>
            </footer>
          </article>
        </transition-group>

        <EmptyState v-if="empty" :kind="q ? 'search' : 'empty'" :title="q ? '没有匹配的订单' : (isRider ? '还没有已完成配送' : '暂无订单')" :hint="q ? '换个关键词试试，支持订单号、店铺、商品、地址' : emptyCopy(role, isRider ? 'done' : 'list')">
          <button v-if="role === 'USER' && !q" class="btn btn-sm" type="button" @click="router.push('/home')">去逛逛</button>
        </EmptyState>
      </div>
    </div>
    <TabBar />
  </div>
</template>

<style scoped>
.orders-top { padding-top: var(--s-3); }
.notice { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; padding: 10px 14px; border-radius: var(--r-md); background: var(--warning-50); color: var(--warning-600); font-size: var(--fs-sm); font-weight: 650; }
.filter-tabs { margin-bottom: 10px; }
.is-dim { filter: grayscale(0.4); opacity: 0.85; }
.order-list { position: relative; display: flex; flex-direction: column; gap: var(--s-3); transition: opacity var(--dur-2) ease; }
.order-list.refreshing { opacity: 0.55; }
.order-card {
  padding: 14px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-1);
  cursor: pointer;
  outline: none;
  transition: transform var(--dur-3) var(--ease-out), box-shadow var(--dur-3) var(--ease-out);
}
@media (hover: hover) { .order-card:hover { transform: translateY(-2px); box-shadow: var(--sh-2); } }
.order-card:active { transform: scale(0.99); }
.order-card:focus-visible { box-shadow: 0 0 0 2px var(--accent); }
.oc-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.oc-shop { display: inline-flex; align-items: center; gap: 6px; min-width: 0; color: var(--text-2); }
.oc-shop b { font-size: var(--fs-md); font-weight: 700; color: var(--text); }
.oc-live { background: var(--success-500); animation: pulse-ring 1.8s ease-out infinite; }
.oc-body { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.oc-thumb { width: 64px; height: 64px; border-radius: 14px; object-fit: cover; flex-shrink: 0; background: var(--ink-150); }
.oc-hint { font-size: var(--fs-xs); color: var(--accent-strong); font-weight: 650; margin-bottom: 2px; }
.oc-goods { font-size: var(--fs-base); font-weight: 600; }
.oc-time { font-size: var(--fs-xs); color: var(--text-3); margin-top: 3px; }
.oc-eta { display: inline-flex; align-items: center; gap: 4px; margin-top: 6px; padding: 3px 8px; border-radius: 6px; background: var(--accent-soft); color: var(--accent-strong); font-size: var(--fs-xs); font-weight: 750; }
.oc-amt { text-align: right; flex-shrink: 0; }
.oc-amt b { display: block; font-size: var(--fs-lg); font-weight: 800; }
.oc-amt small { font-size: var(--fs-2xs); color: var(--text-3); }
.oc-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--line); }
</style>
