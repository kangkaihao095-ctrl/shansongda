<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SsdMap from '../components/SsdMap.vue'
import RoleBadge from '../components/RoleBadge.vue'
import RiderCard from '../components/RiderCard.vue'
import MemberBadge from '../components/MemberBadge.vue'
import CookingStatus from '../components/CookingStatus.vue'
import StarPicker from '../components/StarPicker.vue'
import { etaText, yuan } from '../brand'
import {
  CANCEL_REASONS, DONE_STATUSES, LIVE_TRACK_STATUSES, TIP_GIFTS,
  canMerchantAccept, canRiderArrive, canRiderComplete, canRiderDeliver, canRiderGrab,
  canUserCancel, canUserPay, canUserRefund, formatDateTime, orderStatusHeadline,
  payChannelText, REFUND_REASONS, riderActionText, riderStatusHint, snapshotOf, tipGiftOf
} from '../status'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { fillFromSnapshot } from '../cart'
import { shopIsOpen } from '../merchant'
import { riderCanGrabNew, riderLive } from '../riderLive'
import { goBack, session, toast } from '../session'
import { openNavi } from '../navi'

const route = useRoute()
const router = useRouter()
const track = ref(null)
const formOpen = ref('')
const reasonCode = ref('')
const reasonText = ref('')
const rejectText = ref('')
const giftCode = ref('')
const tipping = ref(false)
let timer

const snap = computed(() => snapshotOf(track.value))
const items = computed(() => snap.value.items || [])
const points = computed(() => {
  const r = track.value?.route || {}
  return r.points?.length ? r.points : [...(r.riderToUser?.points || []), ...(r.riderToMerchant?.points || []), ...(r.merchantToUser?.points || [])]
})
const segments = computed(() => {
  const r = track.value?.route || {}
  if (r.segments?.length) return r.segments
  return [...(r.riderToUser?.segments || []), ...(r.riderToMerchant?.segments || []), ...(r.merchantToUser?.segments || [])]
})
const rider = computed(() => {
  const r = track.value?.rider
  if (r?.lat != null) return { lat: r.lat, lon: r.lon }
  return r?.route?.waypoints?.rider
})
const merchant = computed(() => track.value ? { lat: track.value.merchantLat, lon: track.value.merchantLon } : null)
const user = computed(() => track.value ? { lat: track.value.userLat, lon: track.value.userLon } : null)
const discount = computed(() => snap.value.discountCents || 0)
const payCents = computed(() => track.value?.payAmountCents || ((track.value?.goodsAmountCents || 0) + (track.value?.freightCents || 0) - discount.value))
const canCancel = computed(() => canUserCancel(session.me?.role, track.value?.status))
const canRefund = computed(() => canUserRefund(session.me?.role, track.value?.status))
const deliveringWarn = computed(() => track.value?.status === 'DELIVERING' || track.value?.status === 'ACCEPTED' || track.value?.status === 'ARRIVED')
const done = computed(() => DONE_STATUSES.includes(track.value?.status))
const showMap = computed(() => !done.value && (LIVE_TRACK_STATUSES.includes(track.value?.status) || points.value.length))
const headline = computed(() => orderStatusHeadline(track.value?.status, track.value?.etaMs, session.me?.role))
const statusHint = computed(() => {
  const s = track.value?.status
  if (s === 'COMPLETED') return formatDateTime(track.value?.updatedAt)
  if (session.me?.role === 'RIDER') return riderStatusHint(s)
  if (s === 'PAID' || s === 'MERCHANT_PENDING') return '商家备餐中'
  if (s === 'ACCEPTED') return track.value?.etaMs ? etaText(track.value.etaMs, s) : '骑手正在赶往商家'
  if (s === 'ARRIVED') return '骑手已到店，正在取餐'
  if (s === 'DELIVERING') return ''
  return ''
})
const canReview = computed(() => session.me?.role === 'USER' && track.value?.status === 'COMPLETED' && !track.value?.reviewed)
const canTip = computed(() => session.me?.role === 'USER' && !!track.value?.canTip)
const riderProfile = computed(() => track.value?.riderProfile || (track.value?.rider?.bio ? track.value.rider : null))
const tippedGift = computed(() => tipGiftOf(track.value?.tipGiftCode) || (track.value?.tipGiftLabel ? { label: track.value.tipGiftLabel } : null))
const showUserActions = computed(() => session.me?.role === 'USER' && (canReview.value || (canRefund.value && track.value?.status === 'COMPLETED')))
const merchantOnline = computed(() => shopIsOpen(session.me?.merchant))
const isMerchant = computed(() => session.me?.role === 'MERCHANT')

async function load() {
  try {
    track.value = (await api(`/api/orders/${route.params.id}/track`)).data
  } catch (e) {
    try { track.value = (await api(`/api/orders/${route.params.id}`)).data } catch { toast(e.message, 'err') }
  }
}

async function submitForm() {
  if (!reasonCode.value) return toast('请选择原因', 'err')
  try {
    if (formOpen.value === 'cancel') {
      await api(`/api/orders/${route.params.id}/cancel`, { method: 'POST', body: { reasonCode: reasonCode.value, reasonText: reasonText.value } })
      toast('已进入取消中，请确认')
    } else {
      await api(`/api/orders/${route.params.id}/refund`, { method: 'POST', body: { reasonCode: reasonCode.value, reasonText: reasonText.value } })
      toast('已提交退款申请')
    }
    formOpen.value = ''
    await load()
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function review(approve) {
  try {
    await api(`/api/orders/${route.params.id}/refund-review`, { method: 'POST', body: { approve, reasonText: rejectText.value } })
    toast(approve ? '已同意退款' : '已拒绝退款')
    await load()
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function confirmCancel() {
  try {
    await api(`/api/orders/${route.params.id}/cancel-confirm`, { method: 'POST' })
    toast('订单已取消')
    await load()
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function grabOrder() {
  if (!riderCanGrabNew.value) {
    toast(riderLive.work.forcedOffline ? '今日工时已满或已强制下线，不可接单' : '请先上线', 'err')
    return
  }
  try {
    await api('/api/orders/' + track.value.id + '/grab', { method: 'POST' })
    toast('抢单成功')
    await load()
  } catch (e) {
    toast(e.message, 'err')
  }
}

function copyId() {
  navigator.clipboard?.writeText(String(track.value.id))
  toast('订单号已复制')
}

async function submitTip() {
  if (tipping.value) return
  if (!giftCode.value) return toast('请选择一份礼物', 'err')
  tipping.value = true
  try {
    await api(`/api/orders/${route.params.id}/tip`, { method: 'POST', body: { giftCode: giftCode.value } })
    toast('感谢打赏')
    giftCode.value = ''
    await load()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    tipping.value = false
  }
}

onMounted(async () => {
  await load()
  timer = setInterval(load, 5000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="phone page" :class="{ 'theme-merchant': isMerchant, 'theme-rider': session.me?.role === 'RIDER', 'is-closed': isMerchant && !merchantOnline }">
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router)">← 返回</button>
      <b class="page-title" style="font-size:18px">订单详情</b>
      <RoleBadge />
    </header>
    <div class="phone-body no-tab pad tight" v-if="track">
      <section class="status-hero" :class="{ done: track.status === 'COMPLETED' }">
        <div class="status-title">{{ headline }}</div>
        <div v-if="statusHint" class="muted">{{ statusHint }}</div>
        <span class="pill" style="margin-top:10px">{{ payChannelText(track.payChannel) }}</span>
      </section>
      <div class="muted copy-id" @click="copyId">订单号 {{ track.id }} · 点按复制</div>
      <div v-if="track.refundRejectReason" class="card" style="margin:10px 0;color:#C43D2F">退款拒绝：{{ track.refundRejectReason }}</div>
      <div v-if="track.refundReason" class="muted">退款原因 {{ track.refundReason }}</div>
      <CookingStatus v-if="track.status === 'PAID' || track.status === 'ACCEPTED'" style="margin:10px 0 12px" />
      <SsdMap
        v-if="showMap"
        :points="points"
        :segments="segments"
        :rider="rider"
        :merchant="merchant"
        :user="user"
        :eta="etaText(track.etaMs, track.status)"
        :hint="track.route?.trafficHint"
        tall
      />
      <div class="card" style="margin-top:12px">
        <b>商品</b>
        <div v-for="it in items" :key="it.skuId" class="row" style="margin-top:10px">
          <img :src="imgSrc(it.imageUrl, 'food', 'order-sku-' + it.skuId, 'dish')" alt="" :data-seed="'order-sku-' + it.skuId" data-category="food" @error="onImgError" style="width:48px;height:48px;border-radius:10px;object-fit:cover" />
          <div style="flex:1">{{ it.name }} × {{ it.qty }}</div>
          <span>{{ yuan(it.priceCents) }}</span>
        </div>
      </div>
      <div class="card" style="margin-top:12px">
        <b>金额明细</b>
        <div class="row bill" style="justify-content:space-between"><span>商品合计</span><span>{{ yuan(track.goodsAmountCents) }}</span></div>
        <div class="row bill" style="justify-content:space-between"><span>配送费</span><span>{{ yuan(track.freightCents) }}</span></div>
        <div class="row bill" style="justify-content:space-between"><span>优惠券抵扣</span><span>-{{ yuan(discount) }}</span></div>
        <div class="row bill" style="justify-content:space-between"><b>{{ track.payStatus === 'PAID' || track.paidAt ? '实付' : '应付' }}</b><b>{{ yuan(payCents) }}</b></div>
        <div v-if="track.penaltyCents" class="muted">违约金 {{ yuan(track.penaltyCents) }}</div>
        <div v-if="track.payStatus === 'REFUNDED'" class="muted">退款 {{ yuan(payCents) }} 原路返回{{ payChannelText(track.payChannel) }}</div>
        <div class="muted" style="margin-top:8px">{{ track.addressDetail }}</div>
        <div v-if="track.expectDeliverLabel" class="muted">{{ track.expectDeliverLabel }}</div>
        <div v-if="track.riderIssueText" class="muted" style="color:#C43D2F">骑手上报：{{ track.riderIssueText }}</div>
      </div>
      <div v-if="session.me?.role === 'USER' && track.status === 'COMPLETED'" class="row" style="margin-top:10px">
        <button class="btn" type="button" @click="() => { const s = snapshotOf(track); fillFromSnapshot(track.merchantId, s.shopName, track.coverUrl || s.coverUrl, s.items || []); toast('已加入购物车'); router.push('/checkout') }">再来一单</button>
      </div>
      <div v-if="session.me?.role === 'RIDER' && ['ACCEPTED','ARRIVED','DELIVERING'].includes(track.status)" class="row" style="margin-top:10px">
        <button class="btn" type="button" @click="openNavi(track.status === 'DELIVERING' || track.status === 'ARRIVED' ? track.userLat : track.merchantLat, track.status === 'DELIVERING' || track.status === 'ARRIVED' ? track.userLon : track.merchantLon, track.addressDetail)">导航</button>
      </div>
      <RiderCard v-if="riderProfile" :profile="riderProfile" compact style="margin-top:12px" />
      <div v-if="canTip" class="card" style="margin-top:12px">
        <b>打赏骑手</b>
        <p class="muted" style="margin:6px 0 0">选一份礼物，完成订单可打赏一次</p>
        <div class="gift-grid">
          <button
            v-for="g in TIP_GIFTS"
            :key="g.code"
            type="button"
            class="gift-cell"
            :class="{ on: giftCode === g.code }"
            @click="giftCode = g.code"
          >
            <img :src="g.icon" :alt="g.label" />
            <div class="gift-name">{{ g.label }}</div>
            <div class="gift-price">{{ yuan(g.cents) }}</div>
          </button>
        </div>
        <button class="btn" style="width:100%" :disabled="tipping || !giftCode" @click="submitTip">确认打赏</button>
      </div>
      <div v-else-if="session.me?.role==='USER' && track.status==='COMPLETED' && track.tipped" class="card" style="margin-top:12px">
        已打赏 {{ tippedGift?.label || '' }} {{ yuan(track.tipCents) }}
      </div>
      <div v-if="showUserActions" class="action-row">
        <button v-if="canRefund" class="btn ghost" @click="formOpen='refund'">申请退款</button>
        <button v-if="canReview" class="btn" @click="router.push('/orders/' + track.id + '/review')">评价</button>
      </div>
      <div v-if="canRefund && !(session.me?.role==='USER' && track.status==='COMPLETED')" class="row" style="margin-top:10px">
        <button class="btn ghost" @click="formOpen='refund'">申请退款</button>
      </div>
      <div v-if="canUserPay(session.me?.role, track.status)" class="row" style="margin-top:14px">
        <button class="btn" @click="router.push('/orders/' + track.id + '/pay')">去支付</button>
      </div>
      <div v-if="track.status==='CANCELLING' && session.me?.role==='USER'" class="row" style="margin-top:10px">
        <button class="btn" @click="confirmCancel">确认取消</button>
      </div>
      <div v-if="canCancel && track.status==='CREATED'" class="row" style="margin-top:10px">
        <button class="btn ghost" @click="formOpen='cancel'">取消订单</button>
      </div>
      <div v-if="canRiderGrab(session.me?.role, track.status)" class="row" style="margin-top:10px">
        <p v-if="!riderCanGrabNew" class="muted" style="width:100%;margin:0 0 8px">{{ riderLive.work.forcedOffline ? '今日工时已满，不可再抢新单' : '请先上线后再抢单' }}</p>
        <button class="btn" :disabled="!riderCanGrabNew" @click="grabOrder">{{ riderCanGrabNew ? riderActionText(track.status) : '请先上线' }}</button>
      </div>
      <div v-if="canRiderArrive(session.me?.role, track.status)" class="row" style="margin-top:10px">
        <button class="btn" @click="api('/api/orders/'+track.id+'/arrive',{method:'POST'}).then(load).catch(e=>toast(e.message,'err'))">{{ riderActionText(track.status) }}</button>
      </div>
      <div v-if="canRiderDeliver(session.me?.role, track.status) && track.status==='ARRIVED'" class="row" style="margin-top:10px">
        <button class="btn" @click="api('/api/orders/'+track.id+'/deliver',{method:'POST'}).then(load).catch(e=>toast(e.message,'err'))">{{ riderActionText(track.status) }}</button>
      </div>
      <div v-if="canRiderComplete(session.me?.role, track.status)" class="row" style="margin-top:10px">
        <button class="btn" @click="api('/api/orders/'+track.id+'/complete',{method:'POST'}).then(()=>{toast('已送达');load()}).catch(e=>toast(e.message,'err'))">{{ riderActionText(track.status) }}</button>
      </div>
      <div v-if="canMerchantAccept(session.me?.role, track.status)" class="row" style="margin-top:10px">
        <button class="btn" :disabled="!merchantOnline" :class="{ dim: !merchantOnline }" @click="!merchantOnline ? toast('店铺已打烊，暂不可接新单', 'err') : api('/api/orders/'+track.id+'/merchant-accept',{method:'POST'}).then(load).catch(e=>toast(e.message,'err'))">{{ merchantOnline ? '接单备餐' : '已打烊' }}</button>
        <button class="btn ghost" @click="api('/api/orders/'+track.id+'/merchant-reject',{method:'POST',body:{reasonCode:'STOCK',reasonText:'暂无法出餐'}}).then(load).catch(e=>toast(e.message,'err'))">拒绝</button>
      </div>
      <div v-if="session.me?.role==='USER' && track.status==='COMPLETED' && track.reviewed" class="card" style="margin-top:12px">
        <div class="row" style="justify-content:space-between">
          <div class="row" style="gap:8px">
            <b>我的评价</b>
            <MemberBadge :member="track.review?.member || session.me?.member" compact />
          </div>
        </div>
        <StarPicker :model-value="track.review?.score || 0" size="sm" readonly style="justify-content:flex-start;margin-top:8px" />
        <div v-if="track.review?.riderScore" class="muted" style="margin-top:6px">配送 {{ track.review.riderScore }} 星</div>
        <div v-if="track.review?.content" style="margin-top:8px">{{ track.review.content }}</div>
        <div v-if="track.review?.photoUrls?.length" class="review-photos" style="margin-top:8px">
          <img v-for="url in track.review.photoUrls" :key="url" :src="url" alt="" class="review-thumb" @error="onImgError" />
        </div>
        <div class="muted" style="margin-top:6px">{{ track.review?.skuNames }}</div>
        <button class="btn ghost" style="width:100%;margin-top:10px" @click="router.push('/shops/' + track.merchantId + (track.reviewId ? ('?reviewId=' + track.reviewId) : ''))">查看评价</button>
      </div>
      <div v-if="session.me?.role==='MERCHANT' && track.status==='REFUNDING'" class="card" style="margin-top:12px">
        <input v-model="rejectText" placeholder="拒绝理由（拒绝时填写）" style="width:100%;border:1px solid var(--line);border-radius:12px;padding:10px;margin-bottom:8px" />
        <div class="row">
          <button class="btn" @click="review(true)">同意退款</button>
          <button class="btn ghost" @click="review(false)">拒绝</button>
        </div>
      </div>
      <div v-if="formOpen" class="card" style="margin-top:12px">
        <b>{{ formOpen === 'cancel' ? '取消原因' : '退款原因' }}</b>
        <p v-if="formOpen==='refund' && deliveringWarn" class="muted">配送中/已接单申请退款可能产生违约金，将按现有违约策略计算。</p>
        <button v-for="r in (formOpen==='cancel' ? CANCEL_REASONS : REFUND_REASONS)" :key="r.code" class="chip-plain" :class="{ on: reasonCode===r.code }" type="button" @click="reasonCode=r.code">{{ r.label }}</button>
        <input v-model="reasonText" placeholder="补充说明" style="width:100%;border:1px solid var(--line);border-radius:12px;padding:10px;margin:8px 0" />
        <button class="btn" style="width:100%" @click="submitForm">提交</button>
      </div>
    </div>
  </div>
</template>
