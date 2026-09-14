<script setup>
import { onMounted } from 'vue'
import TabBar from '../components/TabBar.vue'
import WorkRing from '../components/WorkRing.vue'
import RoleBadge from '../components/RoleBadge.vue'
import RiderIncomeChart from '../components/RiderIncomeChart.vue'
import RiderWorkBars from '../components/RiderWorkBars.vue'
import MoneyHover from '../components/MoneyHover.vue'
import { bootRiderLive, riderIncome, riderLive, riderOnline, riderRemainLabel, riderRemainingHours, refreshStats } from '../riderLive'
import { loadMe, session } from '../session'

onMounted(async () => {
  await loadMe()
  await bootRiderLive()
  await refreshStats()
})
</script>

<template>
  <div class="phone page theme-rider">
    <header class="frost pad">
      <div class="row" style="justify-content:space-between">
        <b class="page-title" style="font-size:18px">收入</b>
        <RoleBadge />
      </div>
    </header>
    <div class="phone-body pad">
      <div class="card rider-stats" style="margin-bottom:12px">
        <div class="row" style="gap:16px;align-items:center">
          <WorkRing
            :worked="riderLive.work.workedSecondsToday || 0"
            :max="riderLive.work.maxWorkSeconds || 28800"
            :online="riderOnline"
            :remain-label="riderRemainLabel"
          />
          <div style="flex:1">
            <div class="muted">剩余工作时间</div>
            <div class="num remain-count">{{ riderRemainingHours }}h</div>
            <div class="muted">倒计时 {{ riderRemainLabel }} / 上限 8h</div>
          </div>
        </div>
        <div class="income-grid">
          <MoneyHover :cents="riderIncome" label="预计收入">
            <template #default="{ text }">
              <div class="muted">本月预计</div>
              <div class="num">{{ text }}</div>
            </template>
          </MoneyHover>
          <MoneyHover :cents="riderLive.earn.monthFreightCents" label="运费">
            <template #default="{ text }">
              <div class="muted">运费</div>
              <b>{{ text }}</b>
            </template>
          </MoneyHover>
          <MoneyHover :cents="riderLive.charts.monthTipCents || session.me?.riderProfile?.tipCentsTotal" label="打赏">
            <template #default="{ text }">
              <div class="muted">打赏</div>
              <b>{{ text }}</b>
            </template>
          </MoneyHover>
          <MoneyHover :cents="riderLive.work.dailySubsidyCents" label="日补贴">
            <template #default="{ text }">
              <div class="muted">日补贴</div>
              <b>{{ text }}</b>
            </template>
          </MoneyHover>
        </div>
        <div class="muted" style="margin-top:8px">{{ riderLive.earn.monthIncomeNote || riderLive.work.subsidyNote || '日补贴 + 完成单补贴另列' }}</div>
      </div>
      <div class="card" style="margin-bottom:12px">
        <b>近 7 日收入</b>
        <RiderIncomeChart :series="riderLive.charts.income || []" />
      </div>
      <div class="card" style="margin-bottom:12px">
        <b>近 7 日工时</b>
        <RiderWorkBars :series="riderLive.charts.work || []" :max-seconds="riderLive.work.maxWorkSeconds || 28800" />
      </div>
    </div>
    <TabBar />
  </div>
</template>
