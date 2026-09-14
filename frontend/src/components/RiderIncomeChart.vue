<script setup>
import { computed, ref } from 'vue'
import { yuanExact } from '../brand'
import { chartLabelStep } from '../riderReport'

const props = defineProps({
  series: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false }
})
const w = 360
const h = computed(() => props.compact ? 88 : 150)
const pad = 28
const tip = ref(null)
const wrap = ref(null)

const points = computed(() => {
  const rows = props.series || []
  if (!rows.length) return []
  const max = Math.max(1, ...rows.map((r) => Number(r.totalCents || r.freightCents) || 0))
  const gap = (w - pad * 2) / Math.max(1, rows.length)
  return rows.map((r, i) => {
    const total = Number(r.totalCents || 0) || 0
    const freight = Number(r.freightCents) || 0
    const tipCents = Number(r.tipCents) || 0
    const hh = h.value
    const bh = (total / max) * (hh - pad * 2)
    const x = pad + i * gap + gap * 0.18
    const bw = Math.max(8, gap * 0.64)
    return {
      ...r,
      total,
      freight,
      tipCents,
      x,
      bw,
      y: hh - pad - bh,
      h: bh,
      label: String(r.date || '').slice(5)
    }
  })
})

const chartKey = computed(() => (props.series || []).map((r) => r.date + r.totalCents).join('|'))

function show(p, e) {
  const rect = wrap.value?.getBoundingClientRect()
  tip.value = {
    ...p,
    left: rect ? e.clientX - rect.left : p.x,
    top: rect ? e.clientY - rect.top : p.y
  }
}

function axis(p, i) {
  const n = points.value.length
  const step = chartLabelStep(n)
  if (i !== 0 && i !== n - 1 && i % step !== 0) return ''
  return p.label
}
</script>

<template>
  <div ref="wrap" class="chart-wrap rider-bars" @mouseleave="tip = null">
    <svg :viewBox="`0 0 ${w} ${h}`" class="chart" :class="{ compact }">
      <g :key="chartKey">
        <rect
          v-for="p in points"
          :key="p.date"
          class="income-bar"
          :x="p.x"
          :y="p.y"
          :width="p.bw"
          :height="Math.max(1, p.h)"
          rx="4"
          @mouseenter="show(p, $event)"
          @mousemove="show(p, $event)"
        />
      </g>
      <text v-for="(p, i) in points" :key="'t' + p.date" :x="p.x + p.bw / 2" :y="h - 8" text-anchor="middle" font-size="9" fill="currentColor" opacity="0.7">
        {{ axis(p, i) }}
      </text>
    </svg>
    <div v-if="tip" class="chart-tip" :style="{ left: tip.left + 'px', top: tip.top + 'px' }">
      <b>{{ tip.date }}</b>
      <div>合计 {{ yuanExact(tip.total) }}</div>
      <div>运费 {{ yuanExact(tip.freight) }} · 打赏 {{ yuanExact(tip.tipCents) }}</div>
    </div>
  </div>
</template>

<style scoped>
.chart-wrap { position: relative; }
.chart { width: 100%; height: 150px; color: #1AA7B8; }
.chart.compact { height: 92px; }
</style>
