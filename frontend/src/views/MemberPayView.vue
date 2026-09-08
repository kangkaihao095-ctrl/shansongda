<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { api } from '../api'
import { goBack, loadMe, toast } from '../session'

const PLANS = [
  { plan: 'MONTH', name: '月卡', days: 31, cents: 1500, badge: null },
  { plan: 'QUARTER', name: '季卡', days: 93, cents: 4000, badge: null },
  { plan: 'YEAR', name: '年卡', days: 365, cents: 12800, badge: '年' },
  { plan: 'AUTO_MONTH', name: '连续包月', days: 31, cents: 1200, badge: '连续' }
]

const route = useRoute()
const router = useRouter()
const plans = ref(PLANS)
const plan = ref('YEAR')
const channel = ref('WECHAT')
const password = ref('')
const busy = ref(false)

const current = computed(() => plans.value.find((p) => p.plan === plan.value) || plans.value[2])

onMounted(async () => {
  const q = String(route.query.plan || '')
  if (PLANS.some((p) => p.plan === q)) plan.value = q
  try {
    const data = (await api('/api/member')).data
    if (Array.isArray(data?.plans) && data.plans.length) plans.value = data.plans
  } catch { /* 本地档位兜底 */ }
})

async function pay() {
  if (password.value.length !== 6) return toast('请输入 6 位支付密码', 'err')
  busy.value = true
  try {
    const res = await api('/api/member/subscribe', {
      method: 'POST',
      body: { plan: plan.value, channel: channel.value, password: password.value }
    })
    await loadMe()
    toast(res.data?.message || (current.value.name + '已开通'))
    router.replace('/me')
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
      <button class="back-btn" type="button" @click="goBack(router, '/me')">← 返回</button>
      <b>开通闪会员</b>
    </header>
    <div class="phone-body no-tab pad">
      <div class="card" style="text-align:center;margin-bottom:14px">
        <div class="muted">应付金额</div>
        <div class="num" style="font-size:36px;font-weight:800">{{ yuan(current.cents) }}</div>
        <div class="muted">{{ current.name }} {{ current.days }} 天</div>
      </div>
      <div class="plan-grid">
        <button
          v-for="p in plans"
          :key="p.plan"
          class="plan-card"
          :class="{ on: plan === p.plan, year: p.badge === '年' }"
          type="button"
          @click="plan = p.plan"
        >
          <span v-if="p.badge" class="plan-badge" :class="{ gold: p.badge === '年' }">{{ p.badge }}</span>
          <b>{{ p.name }}</b>
          <div class="num">{{ yuan(p.cents) }}</div>
          <div class="muted">{{ p.days }} 天</div>
        </button>
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
      <button class="btn" style="width:100%;margin-top:16px" :disabled="busy" @click="pay">{{ busy ? '开通中…' : '确认开通' }}</button>
    </div>
  </div>
</template>
