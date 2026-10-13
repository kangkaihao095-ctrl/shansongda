<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SsdMap from '../components/SsdMap.vue'
import RoleBadge from '../components/RoleBadge.vue'
import RiderCard from '../components/RiderCard.vue'
import MemberBadge from '../components/MemberBadge.vue'
import CookingStatus from '../components/CookingStatus.vue'
import StarPicker from '../components/StarPicker.vue'
import PageHeader from '../components/PageHeader.vue'
import BottomSheet from '../components/BottomSheet.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'
import { etaText, freightLabel, yuan } from '../brand'
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
import { session, toast } from '../session'
import { openNavi } from '../navi'
import { clearFocus, focusOrder } from '../stage'

const FLOW = ['CREATED', 'MERCHANT_PENDING', 'PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING', 'COMPLETED']
const FLOW_LABEL = ['下单', '商家接单', '备餐', '骑手接单', '到店取餐', '配送中', '送达']

const route = useRoute()
const router = useRouter()
const track = ref(null)
const loadError = ref('')
const formOpen = ref('')
const reasonCode = ref('')
const reasonText = ref('')
const rejectText = ref('')
const giftCode = ref('')
const tipping = ref(false)
const acting = ref('')
let timer

const role = computed(() => session.me?.role)
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
const canCancel = computed(() => canUserCancel(role.value, track.value?.status))
const canRefund = computed(() => canUserRefund(role.value, track.value?.status))
const deliveringWarn = computed(() => track.value?.status === 'DELIVERING' || track.value?.status === 'ACCEPTED' || track.value?.status === 'ARRIVED')
const done = computed(() => DONE_STATUSES.includes(track.value?.status))
const showMap = computed(() => !done.value && (LIVE_TRACK_STATUSES.includes(track.value?.status) || points.value.length))
const headline = computed(() => orderStatusHeadline(track.value?.status, track.value?.etaMs, role.value))
const statusHint = computed(() => {
  const s = track.value?.status
  if (s === 'COMPLETED') return formatDateTime(track.value?.updatedAt)
  if (role.value === 'RIDER') return riderStatusHint(s)
  if (s === 'PAID' || s === 'MERCHANT_PENDING') return '商家备餐中'
  if (s === 'ACCEPTED') return track.value?.etaMs ? etaText(track.value.etaMs, s) : '骑手正在赶往商家'
  if (s === 'ARRIVED') return '骑手已到店，正在取餐'
  if (s === 'DELIVERING') return ''
  return ''
})
const canReview = computed(() => role.value === 'USER' && track.value?.status === 'COMPLETED' && !track.value?.reviewed)
const canTip = computed(() => role.value === 'USER' && !!track.value?.canTip)
const riderProfile = computed(() => track.value?.riderProfile || (track.value?.rider?.bio ? track.value.rider : null))
const tippedGift = computed(() => tipGiftOf(track.value?.tipGiftCode) || (track.value?.tipGiftLabel ? { label: track.value.tipGiftLabel } : null))
const merchantOnline = computed(() => shopIsOpen(session.me?.merchant))

const flowIndex = computed(() => FLOW.indexOf(track.value?.status))
const heroTone = computed(() => {
  const s = track.value?.status
  if (s === 'COMPLETED') return 'success'
  if (['CANCELLED', 'REFUNDED'].includes(s)) return 'muted'
  if (['REFUNDING', 'REFUND_REJECTED', 'CANCELLING'].includes(s)) return 'warn'
  return 'live'
})

const showPay = computed(() => canUserPay(role.value, track.value?.status))
const showConfirmCancel = computed(() => track.value?.status === 'CANCELLING' && role.value === 'USER')
const showCancel = computed(() => canCancel.value && track.value?.status === 'CREATED')
const showReorder = computed(() => role.value === 'USER' && track.value?.status === 'COMPLETED')
const showGrab = computed(() => canRiderGrab(role.value, track.value?.status))
const showArrive = computed(() => canRiderArrive(role.value, track.value?.status))
const showDeliver = computed(() => canRiderDeliver(role.value, track.value?.status) && track.value?.status === 'ARRIVED')
const showComplete = computed(() => canRiderComplete(role.value, track.value?.status))
const showNavi = computed(() => role.value === 'RIDER' && ['ACCEPTED', 'ARRIVED', 'DELIVERING'].includes(track.value?.status))
const showMerchantAccept = computed(() => canMerchantAccept(role.value, track.value?.status))
const hasBar = computed(() => [showPay, showConfirmCancel, showCancel, showReorder, canRefund, canReview, showGrab, showArrive, showDeliver, showComplete, showNavi, showMerchantAccept].some((c) => c.value))

async function load() {
  try {
    track.value = (await api(`/api/orders/${route.params.id}/track`)).data
    loadError.value = ''
  } catch (e) {
    try {
      track.value = (await api(`/api/orders/${route.params.id}`)).data
      loadError.value = ''
    } catch {
      if (!track.value) loadError.value = e.message || '订单加载失败'
      toast(e.message, 'err')
    }
  }
  if (track.value) focusOrder({ ...track.value, shopName: snap.value.shopName })
}

function openForm(kind) {
  reasonCode.value = ''
  reasonText.value = ''
  formOpen.value = kind
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
  acting.value = 'grab'
  try {
    await api('/api/orders/' + track.value.id + '/grab', { method: 'POST' })
    toast('抢单成功')
    await load()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    acting.value = ''
  }
}

async function postAction(path, okMsg, body) {
  acting.value = path
  try {
    await api('/api/orders/' + track.value.id + '/' + path, body ? { method: 'POST', body } : { method: 'POST' })
    if (okMsg) toast(okMsg)
    await load()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    acting.value = ''
  }
}

function merchantAccept() {
  if (!merchantOnline.value) return toast('店铺已打烊，暂不可接新单', 'err')
  return postAction('merchant-accept')
}

function merchantReject() {
  return postAction('merchant-reject', '', { reasonCode: 'STOCK', reasonText: '暂无法出餐' })
}

function reorder() {
  const s = snapshotOf(track.value)
  fillFromSnapshot(track.value.merchantId, s.shopName, track.value.coverUrl || s.coverUrl, s.items || [])
  toast('已加入购物车')
  router.push('/checkout')
}

function navi() {
  const toUser = track.value.status === 'DELIVERING' || track.value.status === 'ARRIVED'
  openNavi(toUser ? track.value.userLat : track.value.merchantLat, toUser ? track.value.userLon : track.value.merchantLon, track.value.addressDetail)
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
onUnmounted(() => {
  clearInterval(timer)
  clearFocus(route.params.id)
})
</script>

<template>
  <div class="phone page">
    <PageHeader back title="订单详情" :subtitle="snap.shopName || ''">
      <template #right><RoleBadge /></template>
    </PageHeader>

    <div v-if="track" class="phone-body" :class="hasBar ? 'has-bar' : 'no-tab'">
      <div class="pad od-wrap">
        <section class="od-hero" :class="'tone-' + heroTone">
          <div class="od-hero-top">
            <span class="od-status-ico" aria-hidden="true">
              <svg v-if="heroTone === 'success'" class="od-check" viewBox="0 0 52 52"><circle cx="26" cy="26" r="23" /><path d="M15 27l7 7 15-15" /></svg>
              <Icon v-else-if="heroTone === 'muted'" name="close" :size="22" :stroke="2.4" />
              <Icon v-else-if="heroTone === 'warn'" name="alert" :size="22" :stroke="2.2" />
              <Icon v-else name="bike" :size="22" :stroke="2" />
            </span>
            <div class="grow">
              <h2 class="od-title">{{ headline }}</h2>
              <p v-if="statusHint" class="od-hint">{{ statusHint }}</p>
            </div>
          </div>
          <ol v-if="flowIndex >= 0" class="stepper od-steps" aria-label="订单进度">
            <li
              v-for="(s, i) in FLOW"
              :key="s"
              class="step"
              :class="{ done: flowIndex > i || track.status === 'COMPLETED', current: flowIndex === i && track.status !== 'COMPLETED' }"
            >
              <span class="step-dot"><Icon v-if="flowIndex > i || track.status === 'COMPLETED'" name="check" :size="12" :stroke="3" /></span>
              <span class="step-label">{{ FLOW_LABEL[i] }}</span>
            </li>
          </ol>
          <div class="od-meta">
            <button class="od-id" type="button" @click="copyId"><span class="num">订单号 {{ track.id }}</span><Icon name="copy" :size="13" /></button>
            <span class="tag tag-neutral">{{ payChannelText(track.payChannel) }}</span>
          </div>
        </section>

        <div v-if="track.refundRejectReason" class="notice danger"><Icon name="alert" :size="16" />退款被拒绝：{{ track.refundRejectReason }}</div>
        <div v-if="track.refundReason" class="notice warn"><Icon name="info" :size="16" />退款原因：{{ track.refundReason }}</div>
        <div v-if="track.riderIssueText" class="notice danger"><Icon name="alert" :size="16" />骑手上报：{{ track.riderIssueText }}</div>

        <CookingStatus v-if="track.status === 'PAID' || track.status === 'ACCEPTED'" class="od-cook" />

        <section v-if="showMap" class="od-map">
          <SsdMap
            :points="points"
            :segments="segments"
            :rider="rider"
            :merchant="merchant"
            :user="user"
            :eta="etaText(track.etaMs, track.status)"
            :hint="track.route?.trafficHint"
            tall
          />
          <p class="od-map-note"><Icon name="route" :size="13" />看骑手到哪了，颜色越深表示越堵</p>
        </section>

        <RiderCard v-if="riderProfile" :profile="riderProfile" compact />

        <section class="card">
          <div class="card-head">
            <span class="card-title"><Icon name="store" :size="18" />{{ snap.shopName || '商品' }}</span>
            <button v-if="role === 'USER'" class="link-btn" type="button" @click="router.push('/shops/' + track.merchantId)">进店<Icon name="chevron" :size="14" /></button>
          </div>
          <div class="od-items">
            <div v-for="it in items" :key="it.skuId" class="od-item">
              <img :src="imgSrc(it.imageUrl, 'food', 'order-sku-' + it.skuId, 'dish')" alt="" :data-seed="'order-sku-' + it.skuId" data-category="food" @error="onImgError" />
              <span class="grow"><b class="ellipsis">{{ it.name }}</b><small class="num">× {{ it.qty }}</small></span>
              <span class="num">{{ yuan(it.priceCents) }}</span>
            </div>
          </div>
          <hr class="divider dashed" />
          <div class="kv"><span>商品合计</span><span class="num">{{ yuan(track.goodsAmountCents) }}</span></div>
          <div class="kv"><span>配送费 <em v-if="freightLabel(track.freightStrategy)" class="strategy">{{ freightLabel(track.freightStrategy) }}</em></span><span class="num">{{ yuan(track.freightCents) }}</span></div>
          <div class="kv"><span>优惠券抵扣</span><span class="num text-danger">-{{ yuan(discount) }}</span></div>
          <div class="kv total"><span>{{ track.payStatus === 'PAID' || track.paidAt ? '实付' : '应付' }}</span><span class="num">{{ yuan(payCents) }}</span></div>
          <div v-if="track.penaltyCents" class="kv-note text-danger">违约金 {{ yuan(track.penaltyCents) }}（按当前配送进度计算）</div>
          <div v-if="track.payStatus === 'REFUNDED'" class="kv-note">退款 {{ yuan(payCents) }} 原路返回{{ payChannelText(track.payChannel) }}</div>
        </section>

        <section class="card">
          <div class="card-head"><span class="card-title"><Icon name="pin" :size="18" />配送信息</span></div>
          <div class="kv"><span>收货地址</span><span class="od-addr">{{ track.addressDetail || '—' }}</span></div>
          <div v-if="track.expectDeliverLabel" class="kv"><span>期望送达</span><span>{{ track.expectDeliverLabel }}</span></div>
          <div v-if="track.createdAt" class="kv"><span>下单时间</span><span class="num">{{ formatDateTime(track.createdAt) }}</span></div>
          <div v-if="track.paidAt" class="kv"><span>支付时间</span><span class="num">{{ formatDateTime(track.paidAt) }}</span></div>
        </section>

        <section v-if="canTip" class="card tip-card">
          <div class="card-head">
            <span class="card-title"><Icon name="gift" :size="18" />打赏骑手</span>
            <span class="muted">每单可打赏一次</span>
          </div>
          <div class="gift-grid" role="radiogroup" aria-label="选择礼物">
            <button
              v-for="g in TIP_GIFTS"
              :key="g.code"
              type="button"
              class="gift"
              role="radio"
              :aria-checked="giftCode === g.code"
              :class="{ on: giftCode === g.code }"
              @click="giftCode = g.code"
            >
              <img :src="g.icon" alt="" />
              <b>{{ g.label }}</b>
              <span class="num">{{ yuan(g.cents) }}</span>
            </button>
          </div>
          <button class="btn btn-block mt-3" type="button" :disabled="tipping || !giftCode" @click="submitTip">
            <span v-if="tipping" class="spinner"></span>确认打赏
          </button>
        </section>
        <section v-else-if="role === 'USER' && track.status === 'COMPLETED' && track.tipped" class="card tipped">
          <Icon name="heart" :size="18" />已打赏 {{ tippedGift?.label || '' }} <b class="num">{{ yuan(track.tipCents) }}</b>，感谢你的心意
        </section>

        <section v-if="role === 'USER' && track.status === 'COMPLETED' && track.reviewed" class="card">
          <div class="card-head">
            <span class="card-title"><Icon name="star" :size="18" />我的评价</span>
            <MemberBadge :member="track.review?.member || session.me?.member" compact />
          </div>
          <StarPicker :model-value="track.review?.score || 0" size="sm" readonly />
          <div v-if="track.review?.riderScore" class="muted mt-2">配送 {{ track.review.riderScore }} 星</div>
          <p v-if="track.review?.content" class="mt-2">{{ track.review.content }}</p>
          <div v-if="track.review?.photoUrls?.length" class="od-photos">
            <img v-for="url in track.review.photoUrls" :key="url" :src="url" alt="评价配图" @error="onImgError" />
          </div>
          <div class="muted mt-2">{{ track.review?.skuNames }}</div>
          <button class="btn ghost btn-sm btn-block mt-3" type="button" @click="router.push('/shops/' + track.merchantId + (track.reviewId ? ('?reviewId=' + track.reviewId) : ''))">在店铺中查看</button>
        </section>

        <section v-if="role === 'MERCHANT' && track.status === 'REFUNDING'" class="card">
          <div class="card-head"><span class="card-title"><Icon name="alert" :size="18" />退款审核</span></div>
          <input v-model="rejectText" class="field" placeholder="拒绝理由（拒绝时填写）" aria-label="拒绝理由" />
          <div class="row mt-3">
            <button class="btn ghost grow" type="button" @click="review(false)">拒绝退款</button>
            <button class="btn grow" type="button" @click="review(true)">同意退款</button>
          </div>
        </section>
      </div>
    </div>

    <div v-else-if="loadError" class="phone-body no-tab">
      <div class="pad">
        <EmptyState kind="error" title="订单加载失败" :hint="loadError">
          <button class="btn btn-sm" type="button" @click="load"><Icon name="refresh" :size="16" />重试</button>
        </EmptyState>
      </div>
    </div>

    <div v-else class="phone-body no-tab" aria-busy="true">
      <div class="pad od-wrap">
        <div class="skel" style="height:170px;border-radius:24px"></div>
        <div class="skel mt-3" style="height:260px;border-radius:18px"></div>
        <div class="skel mt-3" style="height:180px;border-radius:18px"></div>
      </div>
    </div>

    <div v-if="track && hasBar" class="action-bar od-bar">
      <p v-if="showGrab && !riderCanGrabNew" class="od-bar-tip">{{ riderLive.work.forcedOffline ? '今日工时已满，不可再抢新单' : '请先上线后再抢单' }}</p>
      <button v-if="showCancel" class="btn ghost" type="button" @click="openForm('cancel')">取消订单</button>
      <button v-if="canRefund" class="btn ghost" type="button" @click="openForm('refund')">申请退款</button>
      <button v-if="showNavi" class="btn ghost" type="button" @click="navi"><Icon name="nav" :size="16" />导航</button>
      <button v-if="showMerchantAccept" class="btn ghost" type="button" :disabled="acting === 'merchant-reject'" @click="merchantReject">拒绝</button>
      <button v-if="showReorder" class="btn ghost" type="button" @click="reorder"><Icon name="refresh" :size="16" />再来一单</button>

      <button v-if="showPay" class="btn grow" type="button" @click="router.push('/orders/' + track.id + '/pay')">去支付 {{ yuan(payCents) }}</button>
      <button v-if="showConfirmCancel" class="btn grow" type="button" @click="confirmCancel">确认取消</button>
      <button v-if="canReview" class="btn grow" type="button" @click="router.push('/orders/' + track.id + '/review')"><Icon name="star" :size="16" />评价</button>
      <button v-if="showGrab" class="btn grow" type="button" :disabled="!riderCanGrabNew || acting === 'grab'" @click="grabOrder">
        <span v-if="acting === 'grab'" class="spinner"></span>{{ riderCanGrabNew ? riderActionText(track.status) : '请先上线' }}
      </button>
      <button v-if="showArrive" class="btn grow" type="button" :disabled="acting === 'arrive'" @click="postAction('arrive')">{{ riderActionText(track.status) }}</button>
      <button v-if="showDeliver" class="btn grow" type="button" :disabled="acting === 'deliver'" @click="postAction('deliver')">{{ riderActionText(track.status) }}</button>
      <button v-if="showComplete" class="btn grow" type="button" :disabled="acting === 'complete'" @click="postAction('complete', '已送达')">{{ riderActionText(track.status) }}</button>
      <button v-if="showMerchantAccept" class="btn grow" type="button" :disabled="!merchantOnline || acting === 'merchant-accept'" :class="{ dim: !merchantOnline }" @click="merchantAccept">{{ merchantOnline ? '接单备餐' : '已打烊' }}</button>
    </div>

    <BottomSheet :open="!!formOpen" :title="formOpen === 'cancel' ? '取消原因' : '退款原因'" :subtitle="formOpen === 'cancel' ? '未支付订单先进入「取消中」，确认后取消' : '提交后由商家审核'" @close="formOpen = ''">
      <p v-if="formOpen === 'refund' && deliveringWarn" class="notice warn"><Icon name="alert" :size="16" />骑手已接单或正在配送，这时退款可能会产生违约金</p>
      <div class="chip-row mt-2" role="radiogroup" aria-label="原因">
        <button
          v-for="r in (formOpen === 'cancel' ? CANCEL_REASONS : REFUND_REASONS)"
          :key="r.code"
          class="chip"
          :class="{ on: reasonCode === r.code }"
          type="button"
          role="radio"
          :aria-checked="reasonCode === r.code"
          @click="reasonCode = r.code"
        >{{ r.label }}</button>
      </div>
      <label class="form-label mt-4" for="reason-text">补充说明（选填）</label>
      <input id="reason-text" v-model="reasonText" class="field" placeholder="写下具体情况，方便处理" />
      <template #footer>
        <button class="btn ghost" type="button" @click="formOpen = ''">再想想</button>
        <button class="btn grow" type="button" :disabled="!reasonCode" @click="submitForm">提交</button>
      </template>
    </BottomSheet>
  </div>
</template>

<style scoped>
.od-wrap { padding-top: var(--s-4); display: flex; flex-direction: column; gap: var(--s-3); }
.od-wrap > .card + .card { margin-top: 0; }

.od-hero {
  position: relative;
  overflow: hidden;
  padding: 18px 16px 14px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-2);
  animation: rise var(--dur-4) var(--ease-out) both;
}
.od-hero::before {
  content: ""; position: absolute; inset: 0 0 auto 0; height: 120px;
  background: linear-gradient(180deg, var(--accent-soft), transparent);
  pointer-events: none;
}
.od-hero.tone-success::before { background: linear-gradient(180deg, var(--success-50), transparent); }
.od-hero.tone-muted::before { background: linear-gradient(180deg, var(--ink-100), transparent); }
.od-hero.tone-warn::before { background: linear-gradient(180deg, var(--warning-50), transparent); }
.od-hero-top { position: relative; display: flex; align-items: center; gap: 12px; }
.od-status-ico {
  width: 46px; height: 46px; border-radius: 16px; flex-shrink: 0;
  display: grid; place-items: center;
  background: var(--accent-grad); color: var(--accent-ink);
  box-shadow: var(--sh-accent);
}
.tone-success .od-status-ico { background: var(--success-500); color: #fff; box-shadow: 0 10px 20px -10px rgba(22, 163, 90, 0.6); }
.tone-muted .od-status-ico { background: var(--ink-200); color: var(--ink-600); box-shadow: none; }
.tone-warn .od-status-ico { background: var(--warning-500); color: #fff; box-shadow: none; }
.od-check { width: 30px; height: 30px; }
.od-check circle { fill: none; stroke: rgba(255, 255, 255, 0.5); stroke-width: 3; }
.od-check path { fill: none; stroke: #fff; stroke-width: 4; stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 40; stroke-dashoffset: 40; animation: draw 0.5s 0.2s var(--ease-out) forwards; }
.od-title { font-size: var(--fs-xl); font-weight: 800; letter-spacing: -0.01em; line-height: 1.25; }
.od-hint { margin-top: 2px; font-size: var(--fs-sm); color: var(--text-2); }
.od-steps { position: relative; list-style: none; margin: 18px -6px 0; padding: 0; }
.od-meta { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--line); }
.od-id { display: inline-flex; align-items: center; gap: 4px; font-size: var(--fs-xs); color: var(--text-3); }
.od-id:hover { color: var(--accent-strong); }

.notice { display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: var(--r-md); font-size: var(--fs-sm); font-weight: 650; }
.notice.danger { background: var(--danger-50); color: var(--danger-600); }
.notice.warn { background: var(--warning-50); color: var(--warning-600); }
.od-cook { align-self: flex-start; }

.od-map-note { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: var(--fs-xs); color: var(--text-3); }

.od-items { display: flex; flex-direction: column; gap: 12px; }
.od-item { display: flex; align-items: center; gap: 12px; }
.od-item img { width: 50px; height: 50px; border-radius: 12px; object-fit: cover; flex-shrink: 0; background: var(--ink-150); }
.od-item b { display: block; font-size: var(--fs-base); font-weight: 650; }
.od-item small { display: block; font-size: var(--fs-xs); color: var(--text-3); }
.strategy { font-style: normal; margin-left: 6px; padding: 1px 6px; border-radius: 5px; background: var(--ink-100); color: var(--text-3); font-size: var(--fs-2xs); font-family: var(--font-num); font-weight: 600; }
.od-addr { text-align: right; max-width: 65%; }

.gift-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.gift {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 4px 10px;
  border-radius: var(--r-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--line);
  transition: box-shadow var(--dur-2) ease, background var(--dur-2) ease, transform var(--dur-2) var(--ease-spring);
}
.gift:active { transform: scale(0.95); }
.gift img { width: 40px; height: 40px; transition: transform var(--dur-3) var(--ease-spring); }
.gift b { font-size: var(--fs-xs); font-weight: 700; }
.gift span { font-size: var(--fs-xs); color: var(--flame-600); font-weight: 800; }
.gift.on { background: var(--accent-soft); box-shadow: inset 0 0 0 1.5px var(--accent); }
.gift.on img { transform: scale(1.12) rotate(-6deg); }
.tipped { display: flex; align-items: center; gap: 6px; color: var(--danger-600); font-weight: 650; font-size: var(--fs-sm); }
.od-photos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 10px; }
.od-photos img { width: 100%; aspect-ratio: 1; border-radius: 10px; object-fit: cover; }

.od-bar { flex-wrap: wrap; }
.od-bar .btn { --btn-h: 46px; }
.od-bar-tip { width: 100%; margin: 0 0 2px; font-size: var(--fs-xs); color: var(--warning-600); }
</style>
