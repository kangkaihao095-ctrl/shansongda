<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { api } from '../api'
import { loadMe, toast } from '../session'
import PageHeader from '../components/PageHeader.vue'
import PayPasswordSheet from '../components/PayPasswordSheet.vue'
import Icon from '../components/Icon.vue'

const PLANS = [
  { plan: 'MONTH', name: '月卡', days: 31, cents: 1500, badge: null, perk: '会员运费立减' },
  { plan: 'QUARTER', name: '季卡', days: 93, cents: 4000, badge: null, perk: '一季畅送' },
  { plan: 'YEAR', name: '年卡', days: 365, cents: 12800, badge: '年', perk: 'Lv3+ 再减 1 元' },
  { plan: 'AUTO_MONTH', name: '连续包月', days: 31, cents: 1200, badge: '连续', perk: '每月自动续' }
]

const route = useRoute()
const router = useRouter()
const plans = ref(PLANS)
const plan = ref('YEAR')
const channel = ref('WECHAT')
const sheetOpen = ref(false)
const paidNote = ref('')
const channels = [
  { key: 'WECHAT', name: '微信支付', desc: '推荐 · 模拟收银', icon: '/images/pay-wechat.svg' },
  { key: 'ALIPAY', name: '支付宝', desc: '安全快捷 · 不真实扣款', icon: '/images/pay-alipay.svg' }
]

const current = computed(() => plans.value.find((p) => p.plan === plan.value) || plans.value[2] || plans.value[0])

onMounted(async () => {
  const q = String(route.query.plan || '')
  if (PLANS.some((p) => p.plan === q)) plan.value = q
  try {
    const data = (await api('/api/member')).data
    if (Array.isArray(data?.plans) && data.plans.length) plans.value = data.plans
  } catch { /* 本地档位兜底 */ }
})

function openSheet() {
  if (!current.value || sheetOpen.value) return
  sheetOpen.value = true
}

async function submitPay(pin) {
  const res = await api('/api/member/subscribe', {
    method: 'POST',
    body: { plan: plan.value, channel: channel.value, password: pin }
  })
  paidNote.value = res.data?.message || ((current.value?.name || '会员') + '已开通')
  try { await loadMe() } catch { /* 开通已成功，资料稍后再刷新 */ }
}

function onPaid() {
  toast(paidNote.value || (channel.value === 'ALIPAY' ? '付款成功' : '支付成功'))
  router.replace('/me')
}
</script>

<template>
  <div class="phone page">
    <PageHeader back="/me" title="开通闪会员" subtitle="Lv3+ 运费减 2 元，年卡再减 1 元" />
    <div class="phone-body has-bar">
      <div class="pad member">
        <section class="member-hero">
          <span class="mh-crown"><Icon name="crown" :size="16" :stroke="2.2" />闪会员</span>
          <div class="mh-label">{{ current?.name || '会员' }} · {{ current?.days || 0 }} 天</div>
          <div class="mh-amt num"><small>¥</small>{{ ((current?.cents || 0) / 100).toFixed(2) }}</div>
          <p>距离、高峰和会员等级都会影响运费，券只抵商品。</p>
        </section>

        <div class="kicker mt-6 mb-2">选择档位</div>
        <div class="plan-grid">
          <button
            v-for="p in plans"
            :key="p.plan"
            class="plan-card"
            :class="{ on: plan === p.plan, year: p.badge === '年' || p.plan === 'YEAR' }"
            type="button"
            :aria-pressed="plan === p.plan"
            @click="plan = p.plan"
          >
            <span v-if="p.badge" class="plan-badge" :class="{ gold: p.badge === '年' || p.plan === 'YEAR' }">{{ p.badge }}</span>
            <b>{{ p.name }}</b>
            <div class="num">{{ yuan(p.cents) }}</div>
            <small>{{ p.days }} 天</small>
          </button>
        </div>

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
    </div>
    <div class="action-bar">
      <button class="btn btn-lg btn-block" type="button" @click="openSheet">
        <Icon name="crown" :size="16" />
        确认开通 {{ current ? yuan(current.cents) : '' }}
      </button>
    </div>
    <PayPasswordSheet
      :open="sheetOpen"
      :channel="channel"
      :amount="current ? yuan(current.cents) : ''"
      :merchant="current ? ('闪会员 · ' + current.name) : '闪会员'"
      hint="模拟开通不会真实扣款，体验密码 147258"
      :submit="submitPay"
      @close="sheetOpen = false"
      @change-method="sheetOpen = false"
      @success="onPaid"
    />
  </div>
</template>

<style scoped>
.member { padding-top: var(--s-4); }
.member-hero {
  position: relative;
  overflow: hidden;
  padding: 22px 20px;
  border-radius: var(--r-xl);
  background: radial-gradient(120% 140% at 100% 0%, #4A3414 0%, #1C1610 58%, #12100E 100%);
  color: #fff;
  box-shadow: var(--sh-3);
  animation: rise var(--dur-4) var(--ease-out) both;
}
.member-hero::after {
  content: "";
  position: absolute; right: -30px; top: -40px;
  width: 160px; height: 160px; border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 195, 31, 0.4), transparent 70%);
  pointer-events: none;
}
.mh-crown {
  display: inline-flex; align-items: center; gap: 4px;
  height: 24px; padding: 0 10px;
  border-radius: var(--r-pill);
  background: var(--grad-brand);
  color: #1A1208;
  font-size: var(--fs-xs); font-weight: 800;
}
.mh-label { margin-top: 14px; font-size: var(--fs-sm); color: rgba(255, 255, 255, 0.62); }
.mh-amt { margin-top: 2px; font-size: 40px; font-weight: 800; letter-spacing: -0.03em; }
.mh-amt small { font-size: 20px; margin-right: 2px; color: #FFC31F; }
.member-hero p { margin-top: 8px; max-width: 280px; font-size: var(--fs-xs); color: rgba(255, 255, 255, 0.62); line-height: 1.5; }

.plan-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.plan-card {
  position: relative;
  padding: 14px 12px 12px;
  text-align: left;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
  transition: box-shadow var(--dur-2) ease, transform var(--dur-1) ease;
}
.plan-card:active { transform: scale(0.98); }
.plan-card.on { box-shadow: 0 0 0 1.5px var(--accent), var(--sh-accent); background: var(--accent-soft); }
.plan-card.year.on { background: linear-gradient(180deg, #FFF6DA, #fff); }
.plan-card b { display: block; font-size: var(--fs-md); }
.plan-card .num { margin-top: 6px; font-size: var(--fs-xl); font-weight: 800; }
.plan-card small { display: block; margin-top: 2px; color: var(--text-3); font-size: var(--fs-xs); }
.plan-badge {
  position: absolute; top: 10px; right: 10px;
  height: 18px; padding: 0 6px;
  display: inline-grid; place-items: center;
  border-radius: var(--r-pill);
  background: var(--ink-900); color: #fff;
  font-size: var(--fs-2xs); font-weight: 800;
}
.plan-badge.gold { background: var(--grad-brand); color: #1A1208; }

.channel {
  display: flex; align-items: center; gap: 14px;
  width: 100%; padding: 14px 16px;
  text-align: left;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
}
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
}
.channel.wechat.on .ch-radio { background: #07C160; box-shadow: none; }
.channel.alipay.on .ch-radio { background: #1677FF; box-shadow: none; }
</style>
