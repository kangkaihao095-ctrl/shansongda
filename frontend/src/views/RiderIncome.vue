<script setup>
import { onMounted, ref } from 'vue'
import TabBar from '../components/TabBar.vue'
import WorkRing from '../components/WorkRing.vue'
import RoleBadge from '../components/RoleBadge.vue'
import RiderIncomeChart from '../components/RiderIncomeChart.vue'
import RiderWorkBars from '../components/RiderWorkBars.vue'
import MoneyHover from '../components/MoneyHover.vue'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'
import { bootRiderLive, riderIncome, riderLive, riderOnline, riderRemainLabel, riderRemainingHours, refreshStats } from '../riderLive'
import { loadMe, session } from '../session'

const loading = ref(true)
const loadError = ref('')

onMounted(async () => {
  try {
    await loadMe()
    await bootRiderLive()
    await refreshStats()
  } catch (e) {
    loadError.value = e.message || '收入加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="phone page">
    <PageHeader title="收入" subtitle="运费 + 补贴 + 打赏">
      <template #right><RoleBadge /></template>
    </PageHeader>
    <div class="phone-body pad">
      <div v-if="loading" class="stack-3" aria-busy="true" aria-label="加载中">
        <div class="skel" style="height:180px;border-radius:24px"></div>
        <div class="skel" style="height:160px"></div>
        <div class="skel" style="height:160px"></div>
      </div>
      <EmptyState v-else-if="loadError" kind="error" title="收入加载失败" :hint="loadError">
        <button class="btn btn-sm" type="button" @click="refreshStats"><Icon name="refresh" :size="16" />重试</button>
      </EmptyState>
      <template v-else>
        <section class="earn-hero">
          <div class="earn-ring">
            <WorkRing
              :worked="riderLive.work.workedSecondsToday || 0"
              :max="riderLive.work.maxWorkSeconds || 28800"
              :online="riderOnline"
              :remain-label="riderRemainLabel"
            />
          </div>
          <div class="grow">
            <div class="eh-kicker">{{ riderOnline ? '接单中' : '休息中' }} · 单日上限 8 小时</div>
            <div class="eh-amt num">{{ riderRemainingHours }}<small>h</small></div>
            <p>剩余工时倒计时 {{ riderRemainLabel }}。工时用尽会强制下线。</p>
          </div>
        </section>

        <div class="kpi-grid mt-3">
          <div class="kpi">
            <div class="kpi-label"><Icon name="wallet" :size="13" />本月预计</div>
            <MoneyHover :cents="riderIncome" label="预计收入">
              <template #default="{ text }"><div class="kpi-value">{{ text }}</div></template>
            </MoneyHover>
          </div>
          <div class="kpi">
            <div class="kpi-label"><Icon name="bike" :size="13" />运费</div>
            <MoneyHover :cents="riderLive.earn.monthFreightCents" label="运费">
              <template #default="{ text }"><div class="kpi-value">{{ text }}</div></template>
            </MoneyHover>
          </div>
          <div class="kpi">
            <div class="kpi-label"><Icon name="heart" :size="13" />打赏</div>
            <MoneyHover :cents="riderLive.charts.monthTipCents || session.me?.riderProfile?.tipCentsTotal" label="打赏">
              <template #default="{ text }"><div class="kpi-value">{{ text }}</div></template>
            </MoneyHover>
          </div>
          <div class="kpi">
            <div class="kpi-label"><Icon name="gift" :size="13" />日补贴</div>
            <MoneyHover :cents="riderLive.work.dailySubsidyCents" label="日补贴">
              <template #default="{ text }"><div class="kpi-value">{{ text }}</div></template>
            </MoneyHover>
          </div>
        </div>
        <p class="muted mt-2">{{ riderLive.earn.monthIncomeNote || riderLive.work.subsidyNote || '日补贴与完成单补贴分开计算，完成单另计。' }}</p>

        <section class="card mt-4">
          <div class="card-head">
            <b class="card-title"><Icon name="trend" :size="16" class="ico" />近 7 日收入</b>
          </div>
          <RiderIncomeChart :series="riderLive.charts.income || []" />
          <p v-if="!(riderLive.charts.income || []).length" class="muted">这 7 天还没有入账，完成配送后会按天汇总。</p>
        </section>
        <section class="card">
          <div class="card-head">
            <b class="card-title"><Icon name="clock" :size="16" class="ico" />近 7 日工时</b>
          </div>
          <RiderWorkBars :series="riderLive.charts.work || []" :max-seconds="riderLive.work.maxWorkSeconds || 28800" />
          <p v-if="!(riderLive.charts.work || []).length" class="muted">上线后开始计时，这里会画出每天的在线时长。</p>
        </section>
      </template>
    </div>
    <TabBar />
  </div>
</template>

<style scoped>
.earn-hero {
  display: flex; align-items: center; gap: 4px;
  margin-top: var(--s-4);
  padding: 8px 16px 8px 4px;
  border-radius: var(--r-xl);
  background:
    radial-gradient(90% 120% at 0% 0%, var(--accent-soft), transparent 55%),
    var(--surface);
  box-shadow: var(--sh-2);
  animation: rise var(--dur-4) var(--ease-out) both;
}
.earn-ring { flex-shrink: 0; }
.eh-kicker { font-size: var(--fs-xs); font-weight: 700; color: var(--accent-strong); }
.eh-amt { margin-top: 2px; font-size: 36px; font-weight: 800; letter-spacing: -0.03em; line-height: 1; color: var(--text); }
.eh-amt small { margin-left: 2px; font-size: 16px; font-weight: 700; color: var(--text-3); }
.earn-hero p { margin-top: 6px; font-size: var(--fs-xs); color: var(--text-2); line-height: 1.45; }
</style>
