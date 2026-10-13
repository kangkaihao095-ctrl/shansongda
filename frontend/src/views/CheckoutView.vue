<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { freightLabel, yuan } from '../brand'
import { cart, clearCart, goodsCents } from '../cart'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { session, toast } from '../session'
import PageHeader from '../components/PageHeader.vue'
import BottomSheet from '../components/BottomSheet.vue'
import Icon from '../components/Icon.vue'

const router = useRouter()
const quote = ref(null)
const couponId = ref(null)
const addressId = ref(null)
const expectSlot = ref('ASAP')
const busy = ref(false)
const picker = ref(false)
const booted = ref(false)
const quoting = ref(false)

const addresses = computed(() => session.me?.addresses || [])
const options = computed(() => quote.value?.couponOptions || [])
const selected = computed(() => options.value.find((c) => Number(c.couponId || c.id) === Number(couponId.value)))
const isBest = computed(() => selected.value && Number(quote.value?.suggestedCouponId) === Number(couponId.value))
const availableCount = computed(() => options.value.filter((c) => c.available).length)
const itemsCount = computed(() => cart.items.reduce((s, it) => s + it.qty, 0))

async function refreshQuote() {
  if (!cart.merchantId || !cart.items.length) return
  const aid = addressId.value || (session.me?.addresses || []).find((a) => a.isDefault)?.id || session.me?.addresses?.[0]?.id
  if (!aid) return
  const body = {
    merchantId: cart.merchantId,
    addressId: aid,
    items: cart.items.map((it) => ({ skuId: it.skuId, qty: it.qty, priceCents: it.priceCents })),
    clientPayCents: null
  }
  if (booted.value) body.couponId = couponId.value == null ? 0 : couponId.value
  quoting.value = true
  try {
    quote.value = (await api('/api/orders/preview', { method: 'POST', body })).data
  } finally {
    quoting.value = false
  }
  if (!booted.value) {
    couponId.value = quote.value?.suggestedCouponId ?? null
    booted.value = true
  }
}

onMounted(async () => {
  if (!cart.items.length) {
    toast('购物车是空的', 'err')
    router.replace('/home')
    return
  }
  try {
    addressId.value = (session.me?.addresses || []).find((a) => a.isDefault)?.id
      || session.me?.addresses?.[0]?.id || null
    await refreshQuote()
  } catch (e) {
    toast(e.message, 'err')
  }
})

async function pick(id) {
  couponId.value = id
  picker.value = false
  booted.value = true
  try { await refreshQuote() } catch (e) { toast(e.message, 'err') }
}

async function pickAddress(id) {
  addressId.value = id
  try { await refreshQuote() } catch (e) { toast(e.message, 'err') }
}

async function submit() {
  if (!addressId.value) return toast('请先完善收货地址', 'err')
  busy.value = true
  try {
    const shop = (await api('/api/merchants/' + cart.merchantId)).data
    if (shop?.open === false || shop?.onlineStatus === 'OFFLINE') {
      toast('商家休息，暂不可下单', 'err')
      return
    }
    const res = await api('/api/orders', {
      method: 'POST',
      body: {
        merchantId: cart.merchantId,
        addressId: addressId.value,
        items: cart.items.map((it) => ({ skuId: it.skuId, qty: it.qty, priceCents: it.priceCents })),
        couponId: couponId.value,
        clientPayCents: quote.value?.payCents,
        expectDeliverAt: expectSlot.value
      }
    })
    clearCart()
    router.replace('/orders/' + res.data.id + '/pay')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="phone page">
    <PageHeader back title="确认订单" :subtitle="cart.shopName" />
    <div class="phone-body has-bar">
      <div class="pad stack-3 co-wrap">
        <section v-reveal class="card">
          <div class="card-head">
            <span class="card-title"><Icon name="pin" :size="18" />送达地址</span>
            <button class="link-btn" type="button" @click="router.push('/me/addresses')">管理<Icon name="chevron" :size="14" /></button>
          </div>
          <div class="stack-2" role="radiogroup" aria-label="收货地址">
            <button
              v-for="a in addresses"
              :key="a.id"
              class="addr"
              :class="{ on: addressId === a.id }"
              type="button"
              role="radio"
              :aria-checked="addressId === a.id"
              @click="pickAddress(a.id)"
            >
              <span class="addr-radio" aria-hidden="true"><Icon v-if="addressId === a.id" name="check" :size="12" :stroke="3" /></span>
              <span class="grow">
                <b class="ellipsis">{{ a.detail }}</b>
                <small>{{ a.isDefault ? '默认地址' : '点选使用此地址' }}</small>
              </span>
            </button>
          </div>
          <div v-if="!addresses.length" class="addr-empty">
            <span class="muted">暂无收货地址</span>
            <button class="btn btn-sm" type="button" @click="router.push('/me/addresses')">去添加</button>
          </div>
        </section>

        <section v-reveal="40" class="card">
          <div class="card-head">
            <span class="card-title"><Icon name="clock" :size="18" />送达时间</span>
          </div>
          <div class="slot-grid" role="radiogroup" aria-label="送达时间">
            <button class="slot" :class="{ on: expectSlot === 'ASAP' }" type="button" role="radio" :aria-checked="expectSlot === 'ASAP'" @click="expectSlot = 'ASAP'">
              <Icon name="zap" :size="18" />
              <span><b>尽快送达</b><small>骑手接单后立即配送</small></span>
            </button>
            <button class="slot" :class="{ on: expectSlot === 'WITHIN_1H' }" type="button" role="radio" :aria-checked="expectSlot === 'WITHIN_1H'" @click="expectSlot = 'WITHIN_1H'">
              <Icon name="timer" :size="18" />
              <span><b>1 小时内</b><small>预约时段内送达</small></span>
            </button>
          </div>
        </section>

        <section v-reveal="80" class="card">
          <div class="card-head">
            <span class="card-title"><Icon name="store" :size="18" />{{ cart.shopName }}</span>
            <span class="muted">共 {{ itemsCount }} 件</span>
          </div>
          <div class="items">
            <div v-for="it in cart.items" :key="it.skuId" class="item">
              <img :src="imgSrc(it.imageUrl, 'food', 'sku-' + it.skuId, 'dish')" alt="" :data-seed="'sku-' + it.skuId" data-category="food" @error="onImgError" />
              <span class="grow">
                <b class="ellipsis">{{ it.name }}</b>
                <small>× {{ it.qty }}</small>
              </span>
              <span class="num item-price">{{ yuan(it.priceCents * it.qty) }}</span>
            </div>
          </div>
        </section>

        <button v-reveal="120" class="card coupon-row-card is-interactive" type="button" :disabled="!quote" @click="picker = true">
          <span class="cr-ico"><Icon name="ticket" :size="20" /></span>
          <span class="grow">
            <b>优惠券</b>
            <small v-if="selected">
              <span v-if="isBest" class="best"><Icon name="sparkles" :size="12" />已自动选择最优</span>
              {{ selected.name }}
            </small>
            <small v-else>{{ availableCount ? `${availableCount} 张可用` : '暂无可用优惠券' }}</small>
          </span>
          <span v-if="selected" class="cr-off num">-{{ yuan(selected.discountCents) }}</span>
          <Icon name="chevron" :size="16" class="subtle" />
        </button>

        <section v-reveal="160" class="card bill">
          <div class="card-head">
            <span class="card-title"><Icon name="receipt" :size="18" />金额明细</span>
            <span v-if="quoting" class="spinner bill-spin" aria-label="计算中"></span>
          </div>
          <template v-if="quote">
            <div class="kv"><span>商品合计</span><span class="num">{{ yuan(quote.goodsAmountCents || goodsCents()) }}</span></div>
            <div v-if="quote.shopPromoCents" class="kv"><span>店铺满减</span><span class="num text-danger">-{{ yuan(quote.shopPromoCents) }}</span></div>
            <div v-if="quote.shopPromoNote" class="kv-note">{{ quote.shopPromoNote }}</div>
            <div class="kv">
              <span>配送费 <em v-if="freightLabel(quote.freightStrategy)" class="strategy">{{ freightLabel(quote.freightStrategy) }}</em></span>
              <span class="num">{{ yuan(quote.freightCents) }}</span>
            </div>
            <div v-if="quote.memberFreightOffCents" class="kv-note"><Icon name="crown" :size="12" /> 含闪会员运费减免 {{ yuan(quote.memberFreightOffCents) }}</div>
            <div class="kv"><span>优惠券抵扣</span><span class="num text-danger">-{{ yuan(quote.discountCents) }}</span></div>
            <div class="kv-note">{{ quote.couponCoversFreight ? '本券可抵运费' : '已自动选最划算的券，运费券才抵配送费' }}</div>
            <div class="kv total"><span>应付</span><span class="num">{{ yuan(quote.payCents) }}</span></div>
          </template>
          <template v-else>
            <div v-for="n in 4" :key="n" class="kv"><span class="skel skel-line" style="width:72px"></span><span class="skel skel-line" style="width:56px"></span></div>
            <p v-if="!addresses.length" class="muted mt-3">添加收货地址后自动计算运费与优惠</p>
          </template>
        </section>
      </div>
    </div>

    <div class="action-bar">
      <div class="bar-sum">
        <small>应付金额</small>
        <b class="num">{{ quote ? yuan(quote.payCents) : '—' }}</b>
      </div>
      <button class="btn btn-lg" type="button" :disabled="busy || !quote" @click="submit">
        <span v-if="busy" class="spinner"></span>{{ busy ? '提交中…' : '提交订单' }}
      </button>
    </div>

    <BottomSheet :open="picker" title="选择优惠券" subtitle="结算预览已按应付最低为你推荐" @close="picker = false">
      <div class="stack-2">
        <button class="cp-opt" :class="{ on: couponId == null || couponId === 0 }" type="button" @click="pick(0)">
          <span class="cp-main"><b>不使用优惠券</b></span>
          <span class="cp-radio" aria-hidden="true"><Icon v-if="couponId == null || couponId === 0" name="check" :size="12" :stroke="3" /></span>
        </button>
        <button
          v-for="c in options"
          :key="c.couponId || c.id"
          class="cp-opt"
          :class="{ on: Number(couponId) === Number(c.couponId || c.id), off: !c.available }"
          type="button"
          :disabled="!c.available"
          @click="c.available && pick(c.couponId || c.id)"
        >
          <span class="cp-amt num">-{{ yuan(c.discountCents) }}</span>
          <span class="cp-main">
            <b>{{ c.name }}<span v-if="Number(quote?.suggestedCouponId) === Number(c.couponId || c.id)" class="tag tag-brand">最优</span></b>
            <small>{{ c.coversFreight === true ? '可抵运费' : '只抵商品、不抵运费' }} · 满 {{ yuan(c.minSpendCents) }}</small>
            <small v-if="c.available" class="text-accent">使用后应付 {{ yuan(c.payCents) }}</small>
            <small v-else class="text-danger">{{ c.reason }}</small>
          </span>
          <span class="cp-radio" aria-hidden="true"><Icon v-if="Number(couponId) === Number(c.couponId || c.id)" name="check" :size="12" :stroke="3" /></span>
        </button>
      </div>
    </BottomSheet>
  </div>
</template>

<style scoped>
.co-wrap { padding-top: var(--s-4); }
.co-wrap > .card + .card { margin-top: 0; }
.addr {
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 12px;
  text-align: left;
  border-radius: var(--r-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--line);
  transition: box-shadow var(--dur-2) ease, background var(--dur-2) ease;
}
.addr.on { background: var(--accent-soft); box-shadow: inset 0 0 0 1.5px var(--accent); }
.addr b { display: block; font-size: var(--fs-base); font-weight: 650; }
.addr small { display: block; font-size: var(--fs-xs); color: var(--text-3); margin-top: 1px; }
.addr-radio, .cp-radio {
  width: 20px; height: 20px; border-radius: 50%; flex-shrink: 0;
  display: grid; place-items: center;
  box-shadow: inset 0 0 0 1.5px var(--ink-300);
  color: var(--accent-ink);
  transition: background var(--dur-2) ease, box-shadow var(--dur-2) ease;
}
.addr.on .addr-radio, .cp-opt.on .cp-radio { background: var(--accent); box-shadow: none; }
.addr-empty { display: flex; align-items: center; justify-content: space-between; padding: 10px 0 2px; }

.slot-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.slot {
  display: flex; align-items: center; gap: 10px;
  padding: 12px;
  text-align: left;
  border-radius: var(--r-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--line);
  color: var(--text-2);
  transition: box-shadow var(--dur-2) ease, background var(--dur-2) ease, color var(--dur-2) ease;
}
.slot b { display: block; font-size: var(--fs-base); color: var(--text); }
.slot small { display: block; font-size: var(--fs-2xs); color: var(--text-3); margin-top: 1px; }
.slot.on { background: var(--accent-soft); box-shadow: inset 0 0 0 1.5px var(--accent); color: var(--accent-strong); }

.items { display: flex; flex-direction: column; gap: 12px; }
.item { display: flex; align-items: center; gap: 12px; }
.item img { width: 52px; height: 52px; border-radius: 12px; object-fit: cover; flex-shrink: 0; background: var(--ink-150); }
.item b { display: block; font-size: var(--fs-base); font-weight: 650; }
.item small { display: block; font-size: var(--fs-xs); color: var(--text-3); font-family: var(--font-num); }
.item-price { font-weight: 700; }

.coupon-row-card { display: flex; align-items: center; gap: 12px; width: 100%; text-align: left; }
.coupon-row-card:disabled { opacity: 0.6; }
.cr-ico { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; background: var(--danger-50); color: var(--danger-600); flex-shrink: 0; }
.coupon-row-card b { display: block; font-size: var(--fs-md); }
.coupon-row-card small { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 2px; font-size: var(--fs-xs); color: var(--text-3); }
.best { display: inline-flex; align-items: center; gap: 2px; height: 18px; padding: 0 6px; border-radius: 5px; background: var(--grad-brand); color: #1A1208; font-weight: 800; font-size: var(--fs-2xs); }
.cr-off { font-weight: 800; color: var(--danger-600); font-size: var(--fs-md); }

.bill-spin { width: 14px; height: 14px; color: var(--text-3); }
.strategy { font-style: normal; margin-left: 6px; padding: 1px 6px; border-radius: 5px; background: var(--ink-100); color: var(--text-3); font-size: var(--fs-2xs); font-family: var(--font-num); font-weight: 600; }
.kv-note { display: flex; align-items: center; gap: 4px; }

.cp-opt {
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 14px;
  text-align: left;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
  transition: box-shadow var(--dur-2) ease;
}
.cp-opt.on { box-shadow: 0 0 0 1.5px var(--accent), var(--sh-1); }
.cp-opt.off { opacity: 0.55; }
.cp-amt { min-width: 64px; font-size: var(--fs-xl); font-weight: 800; color: var(--danger-600); }
.cp-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cp-main b { display: flex; align-items: center; gap: 6px; font-size: var(--fs-base); }
.cp-main small { font-size: var(--fs-xs); color: var(--text-3); }
</style>
