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
import { session, toast } from '../session'

const router = useRouter()
const filter = ref('')
const role = computed(() => session.me?.role)
const isRider = computed(() => role.value === 'RIDER')
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
  const need = (rows || []).filter((o) => ['PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING'].includes(o.status))
  await Promise.all(need.map(async (o) => {
    try {
      const t = (await api(`/api/orders/${o.id}/track`)).data
      o.etaMs = t.etaMs
      if (!o.etaMs) o.etaMs = (await api(`/api/orders/${o.id}/route`)).data.etaMs
    } catch { o.etaMs = null }
  }))
}

async function reload() {
  await load()
  await attachEta(items.value)
}

async function act(path, id) {
  try {
    await api(`/api/orders/${id}/${path}`, { method: 'POST' })
    toast('已更新')
    await reload()
  } catch (e) { toast(e.message, 'err') }
}

function cover(o) {
  return imgSrc(o.coverUrl || snapshotOf(o).items?.[0]?.imageUrl, 'food', 'order-' + o.id, 'shop')
}

watch(filter, () => { page.value = 1; reload() })
onMounted(reload)
</script>

<template>
  <div class="phone page">
    <header class="frost pad row" style="justify-content:space-between">
      <b>{{ title }}</b>
      <RoleBadge />
    </header>
    <div class="phone-body pad">
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
      <article v-for="o in items" :key="o.id" class="card" style="margin-bottom:12px" @click="router.push('/orders/' + o.id)">
        <div class="row">
          <img :src="cover(o)" alt="" :data-seed="'order-' + o.id" @error="onImgError" style="width:56px;height:56px;border-radius:12px;object-fit:cover" />
          <div style="flex:1">
            <div class="row" style="justify-content:space-between">
              <b>#{{ o.id }}</b>
              <span class="pill">{{ orderStatusHeadline(o.status, o.etaMs, role) }}</span>
            </div>
            <div v-if="isRider && riderStatusHint(o.status)" class="muted">{{ riderStatusHint(o.status) }}</div>
            <div class="muted">{{ o.addressDetail }}</div>
            <div v-if="['PAID','ACCEPTED','ARRIVED','DELIVERING'].includes(o.status)" class="muted">{{ etaLabel(o.etaMs, o.status) }}</div>
          </div>
        </div>
        <div class="num" style="margin-top:6px">{{ yuan((o.payAmountCents) || (o.goodsAmountCents + o.freightCents)) }}</div>
        <div v-if="!isRider" class="row" style="margin-top:8px" @click.stop>
          <button v-if="canUserPay(role, o.status)" class="btn" @click="router.push('/orders/' + o.id + '/pay')">支付</button>
          <button v-if="canMerchantAccept(role, o.status)" class="btn" @click="act('merchant-accept', o.id)">接单</button>
          <button v-if="canMerchantAccept(role, o.status)" class="btn ghost" @click="api('/api/orders/'+o.id+'/merchant-reject',{method:'POST',body:{reasonCode:'STOCK',reasonText:'暂无法出餐'}}).then(reload).catch(e=>toast(e.message,'err'))">拒绝</button>
          <button v-if="role === 'USER' && o.status === 'COMPLETED' && !o.reviewed" class="btn" @click="router.push('/orders/' + o.id + '/review')">评价</button>
          <button v-if="role === 'USER' && o.status === 'COMPLETED' && o.reviewed" class="btn ghost" @click="router.push('/shops/' + o.merchantId + (o.reviewId ? ('?reviewId=' + o.reviewId) : ''))">查看评价</button>
        </div>
      </article>
      <EmptyState v-if="empty" :title="isRider ? '还没有已完成配送' : '暂无订单'" :hint="emptyCopy(role, isRider ? 'done' : 'list')" />
    </div>
    <TabBar />
  </div>
</template>
