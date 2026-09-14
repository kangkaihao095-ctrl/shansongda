<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { yuan } from '../brand'
import { cart, clearCart, goodsCents } from '../cart'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { goBack, session, toast } from '../session'

const router = useRouter()
const quote = ref(null)
const couponId = ref(null)
const addressId = ref(null)
const expectSlot = ref('ASAP')
const busy = ref(false)
const picker = ref(false)
const booted = ref(false)

const addresses = computed(() => session.me?.addresses || [])
const options = computed(() => quote.value?.couponOptions || [])
const selected = computed(() => options.value.find((c) => Number(c.couponId || c.id) === Number(couponId.value)))

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
  quote.value = (await api('/api/orders/preview', { method: 'POST', body })).data
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
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router)">← 返回</button>
      <b class="page-title" style="font-size:18px">确认订单</b>
    </header>
    <div class="phone-body no-tab pad">
      <div class="card" style="margin-bottom:12px">
        <div class="muted" style="margin-bottom:4px">收货地址</div>
        <button v-for="a in addresses" :key="a.id" class="addr-pick" :class="{ on: addressId === a.id }" type="button" @click="pickAddress(a.id)">
          <b>{{ a.detail }}</b>
          <div class="muted">{{ a.isDefault ? '默认地址' : '点选使用' }}</div>
        </button>
        <p v-if="!addresses.length" class="muted">暂无地址，请到个人中心添加</p>
      </div>
      <div class="card" style="margin-bottom:12px">
        <b>{{ cart.shopName }}</b>
        <div v-for="it in cart.items" :key="it.skuId" class="row" style="margin-top:10px">
          <img :src="imgSrc(it.imageUrl, 'food', 'sku-' + it.skuId, 'dish')" alt="" :data-seed="'sku-' + it.skuId" data-category="food" @error="onImgError" style="width:48px;height:48px;border-radius:10px;object-fit:cover" />
          <div style="flex:1">{{ it.name }} × {{ it.qty }}</div>
          <span>{{ yuan(it.priceCents * it.qty) }}</span>
        </div>
      </div>
      <div class="card" style="margin-bottom:12px">
        <b>送达时间</b>
        <div class="row" style="margin-top:8px">
          <button class="coupon-pick" :class="{ on: expectSlot === 'ASAP' }" type="button" @click="expectSlot = 'ASAP'">尽快送达</button>
          <button class="coupon-pick" :class="{ on: expectSlot === 'WITHIN_1H' }" type="button" @click="expectSlot = 'WITHIN_1H'">1 小时内</button>
        </div>
      </div>
      <div class="card" style="margin-bottom:12px">
        <div class="row" style="justify-content:space-between">
          <b>优惠券</b>
          <button class="btn ghost" type="button" @click="picker = !picker">换一张</button>
        </div>
        <div v-if="selected" class="muted" style="margin-top:8px">
          已自动勾选最优：{{ selected.name }} · 抵 {{ yuan(selected.discountCents) }}
        </div>
        <div v-else class="muted" style="margin-top:8px">未使用优惠券</div>
        <div v-if="picker" style="margin-top:10px">
          <button class="coupon-pick" :class="{ on: couponId == null || couponId === 0 }" type="button" @click="pick(0)">不使用优惠券</button>
          <button
            v-for="c in options"
            :key="c.couponId || c.id"
            class="coupon-pick"
            :class="{ on: Number(couponId) === Number(c.couponId || c.id), off: !c.available }"
            type="button"
            @click="c.available && pick(c.couponId || c.id)"
          >
            <div>{{ c.name }} · {{ c.available ? '应付 ' + yuan(c.payCents) : c.reason }}</div>
            <div class="muted">{{ c.coversFreight === true ? '可抵运费' : '只抵商品、不抵运费' }} · 满 {{ yuan(c.minSpendCents) }} {{ c.available ? '可用' : '' }}</div>
          </button>
        </div>
      </div>
      <div class="card">
        <div class="row" style="justify-content:space-between"><span>商品合计</span><span>{{ yuan(quote?.goodsAmountCents || goodsCents()) }}</span></div>
        <div v-if="quote?.shopPromoCents" class="row" style="justify-content:space-between"><span>店铺满减</span><span>-{{ yuan(quote.shopPromoCents) }}</span></div>
        <div v-if="quote?.shopPromoNote" class="muted">{{ quote.shopPromoNote }}</div>
        <div class="row" style="justify-content:space-between"><span>配送费</span><span>{{ yuan(quote?.freightCents) }}</span></div>
        <div v-if="quote?.memberFreightOffCents" class="muted">含闪会员运费减免 {{ yuan(quote.memberFreightOffCents) }} · {{ quote.freightStrategy }}</div>
        <div class="row" style="justify-content:space-between"><span>优惠券抵扣</span><span>-{{ yuan(quote?.discountCents) }}</span></div>
        <div class="muted">{{ quote?.couponCoversFreight ? '本券可抵运费' : '平台券默认只抵商品；运费券除外' }}</div>
        <div class="row" style="justify-content:space-between;margin-top:8px"><b>应付</b><b class="num" style="font-size:20px">{{ yuan(quote?.payCents) }}</b></div>
      </div>
      <button class="btn checkout-bar" style="width:100%" :disabled="busy || !quote" @click="submit">提交订单并支付</button>
    </div>
  </div>
</template>
