<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { snapshotOf } from '../status'
import { api } from '../api'
import { goBack, toast } from '../session'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const channel = ref('WECHAT')
const password = ref('')
const busy = ref(false)
const snap = computed(() => snapshotOf(order.value))
const payCents = computed(() => order.value?.payAmountCents || ((order.value?.goodsAmountCents || 0) + (order.value?.freightCents || 0) - (snap.value.discountCents || 0)))

onMounted(async () => {
  try {
    order.value = (await api('/api/orders/' + route.params.id)).data
    if (order.value.status !== 'CREATED') router.replace('/orders/' + route.params.id)
  } catch (e) {
    toast(e.message, 'err')
  }
})

async function pay() {
  if (password.value.length !== 6) return toast('请输入 6 位支付密码', 'err')
  busy.value = true
  try {
    await api(`/api/orders/${route.params.id}/pay`, { method: 'POST', body: { channel: channel.value, password: password.value } })
    toast('支付成功')
    router.replace('/orders/' + route.params.id)
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
      <b class="page-title" style="font-size:18px">收银台</b>
    </header>
    <div class="phone-body no-tab pad" v-if="order">
      <div class="card pay-hero">
        <div class="muted">应付金额</div>
        <div class="num">{{ yuan(payCents) }}</div>
        <div class="muted">商品 {{ yuan(order.goodsAmountCents) }} · 配送 {{ yuan(order.freightCents) }} · 优惠 -{{ yuan(snap.discountCents) }}</div>
      </div>
      <button class="pay-card" :class="{ on: channel==='WECHAT' }" type="button" @click="channel='WECHAT'">
        <img src="/images/pay-wechat.svg" alt="微信支付" />
        <div><b>微信支付</b><div class="muted">绿色安全收银台</div></div>
      </button>
      <button class="pay-card alipay" :class="{ on: channel==='ALIPAY' }" type="button" @click="channel='ALIPAY'">
        <img src="/images/pay-alipay.svg" alt="支付宝" />
        <div><b>支付宝</b><div class="muted">蓝色快捷收银台</div></div>
      </button>
      <div class="card" style="margin-top:14px">
        <div class="muted" style="margin-bottom:8px">支付密码</div>
        <input class="pin" v-model="password" maxlength="6" inputmode="numeric" placeholder="6 位数字" />
        <div class="muted" style="margin-top:8px">密码 147258</div>
      </div>
      <button class="btn" style="width:100%;margin-top:16px" :disabled="busy" @click="pay">{{ busy ? '支付处理中…' : '确认支付' }}</button>
      <button class="btn ghost" style="width:100%;margin-top:10px" @click="goBack(router)">关闭支付</button>
    </div>
    <div class="phone-body no-tab pad" v-else>
      <p class="muted">订单加载中，若长时间无内容请返回列表重试。</p>
    </div>
  </div>
</template>
