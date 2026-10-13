<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { snapshotOf } from '../status'
import { api } from '../api'
import { goBack, toast } from '../session'
import { clearFocus, focusOrder } from '../stage'
import PageHeader from '../components/PageHeader.vue'
import PayPasswordSheet from '../components/PayPasswordSheet.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loadError = ref('')
const channel = ref('WECHAT')
const sheetOpen = ref(false)
const snap = computed(() => snapshotOf(order.value))
const payCents = computed(() => order.value?.payAmountCents || ((order.value?.goodsAmountCents || 0) + (order.value?.freightCents || 0) - (snap.value.discountCents || 0)))
const channels = [
  { key: 'WECHAT', name: '微信支付', desc: '推荐 · 亿万用户的选择', icon: '/images/pay-wechat.svg' },
  { key: 'ALIPAY', name: '支付宝', desc: '安全快捷 · 花呗可用', icon: '/images/pay-alipay.svg' }
]

async function load() {
  loadError.value = ''
  try {
    order.value = (await api('/api/orders/' + route.params.id)).data
    focusOrder({ ...order.value, shopName: snapshotOf(order.value).shopName })
    if (order.value.status !== 'CREATED') router.replace('/orders/' + route.params.id)
  } catch (e) {
    loadError.value = e.message || '订单加载失败'
    toast(e.message, 'err')
  }
}

onMounted(load)
onUnmounted(() => clearFocus(route.params.id))

function openSheet() {
  if (!order.value || sheetOpen.value) return
  sheetOpen.value = true
}

async function submitPay(pin) {
  await api(`/api/orders/${route.params.id}/pay`, {
    method: 'POST',
    body: { channel: channel.value, password: pin }
  })
}

function onPaid() {
  toast(channel.value === 'ALIPAY' ? '付款成功' : '支付成功')
  router.replace('/orders/' + route.params.id)
}
</script>

<template>
  <div class="phone page">
    <PageHeader back title="收银台" :subtitle="order ? '订单号 ' + order.id : ''" />
    <div class="phone-body has-bar">
      <div v-if="order" class="pad pay-wrap">
        <section class="pay-hero">
          <div class="ph-shield"><Icon name="shield" :size="14" />闪送达安全收银</div>
          <div class="ph-label">应付金额</div>
          <div class="ph-amt num"><small>¥</small>{{ (payCents / 100).toFixed(2) }}</div>
          <div class="ph-break">
            <span>商品 <b class="num">{{ yuan(order.goodsAmountCents) }}</b></span>
            <span>配送 <b class="num">{{ yuan(order.freightCents) }}</b></span>
            <span>优惠 <b class="num">-{{ yuan(snap.discountCents) }}</b></span>
          </div>
          <div v-if="snap.shopName" class="ph-shop"><Icon name="store" :size="14" />{{ snap.shopName }}</div>
        </section>

        <div class="kicker mt-6 mb-2">支付方式</div>
        <div class="stack-2" role="radiogroup" aria-label="支付方式">
          <button
            v-for="c in channels"
            :key="c.key"
            class="channel"
            :class="[c.key.toLowerCase(), { on: channel === c.key }]"
            type="button"
            role="radio"
            :aria-checked="channel === c.key"
            @click="channel = c.key"
          >
            <img :src="c.icon" :alt="c.name" />
            <span class="grow"><b>{{ c.name }}</b><small>{{ c.desc }}</small></span>
            <span class="ch-radio" aria-hidden="true"><Icon v-if="channel === c.key" name="check" :size="12" :stroke="3" /></span>
          </button>
        </div>

      </div>

      <div v-else-if="loadError" class="pad">
        <EmptyState kind="error" title="订单加载失败" :hint="loadError">
          <button class="btn btn-sm" type="button" @click="load"><Icon name="refresh" :size="16" />重试</button>
        </EmptyState>
      </div>

      <div v-else class="pad pay-wrap" aria-busy="true">
        <div class="skel" style="height:200px;border-radius:24px"></div>
        <div class="skel mt-6" style="height:68px"></div>
        <div class="skel mt-2" style="height:68px"></div>
      </div>
    </div>

    <div class="action-bar">
      <button class="btn ghost" type="button" @click="goBack(router)">稍后支付</button>
      <button class="btn btn-lg grow" type="button" :disabled="!order" @click="openSheet">
        确认支付 {{ order ? yuan(payCents) : '' }}
      </button>
    </div>
    <PayPasswordSheet
      :open="sheetOpen && !!order"
      :channel="channel"
      :amount="order ? yuan(payCents) : ''"
      :merchant="snap.shopName || '闪送达'"
      hint="模拟支付不会真实扣款，体验密码 147258"
      :submit="submitPay"
      @close="sheetOpen = false"
      @change-method="sheetOpen = false"
      @success="onPaid"
    />
  </div>
</template>

<style scoped>
.pay-wrap { padding-top: var(--s-4); }
.pay-hero {
  position: relative;
  overflow: hidden;
  padding: 22px 20px 20px;
  border-radius: var(--r-xl);
  background: radial-gradient(120% 120% at 100% 0%, #3A2A14 0%, #1C1712 55%, #141110 100%);
  color: #fff;
  text-align: center;
  box-shadow: var(--sh-3);
  animation: rise var(--dur-4) var(--ease-out) both;
}
.pay-hero::after {
  content: "";
  position: absolute; right: -40px; top: -60px;
  width: 180px; height: 180px; border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 179, 0, 0.35), transparent 70%);
  pointer-events: none;
}
.ph-shield { display: inline-flex; align-items: center; gap: 4px; height: 24px; padding: 0 10px; border-radius: var(--r-pill); background: rgba(74, 222, 128, 0.14); color: #4ADE80; font-size: var(--fs-xs); font-weight: 700; }
.ph-label { margin-top: 14px; font-size: var(--fs-sm); color: rgba(255, 255, 255, 0.6); }
.ph-amt { margin-top: 4px; font-size: 44px; font-weight: 800; letter-spacing: -0.03em; line-height: 1.1; }
.ph-amt small { font-size: 22px; margin-right: 2px; color: #FFC31F; }
.ph-break { display: flex; justify-content: center; gap: 14px; margin-top: 12px; font-size: var(--fs-xs); color: rgba(255, 255, 255, 0.55); flex-wrap: wrap; }
.ph-break b { color: rgba(255, 255, 255, 0.9); font-weight: 650; }
.ph-shop { display: inline-flex; align-items: center; gap: 4px; margin-top: 12px; font-size: var(--fs-xs); color: rgba(255, 255, 255, 0.55); }

.channel {
  display: flex; align-items: center; gap: 14px;
  width: 100%; padding: 14px 16px;
  text-align: left;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
  transition: box-shadow var(--dur-2) ease, transform var(--dur-1) ease;
}
.channel:active { transform: scale(0.99); }
.channel img { width: 40px; height: 40px; border-radius: 12px; }
.channel b { display: block; font-size: var(--fs-md); }
.channel small { display: block; font-size: var(--fs-xs); color: var(--text-3); margin-top: 1px; }
.channel.wechat.on { box-shadow: 0 0 0 1.5px #07C160, 0 10px 24px -14px rgba(7, 193, 96, 0.6); }
.channel.alipay.on { box-shadow: 0 0 0 1.5px #1677FF, 0 10px 24px -14px rgba(22, 119, 255, 0.6); }
.ch-radio {
  width: 22px; height: 22px; border-radius: 50%; flex-shrink: 0;
  display: grid; place-items: center;
  box-shadow: inset 0 0 0 1.5px var(--ink-300);
  color: #fff;
  transition: background var(--dur-2) ease;
}
.channel.wechat.on .ch-radio { background: #07C160; box-shadow: none; }
.channel.alipay.on .ch-radio { background: #1677FF; box-shadow: none; }
</style>
