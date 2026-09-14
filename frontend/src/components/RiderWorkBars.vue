<script setup>
import { computed, ref } from 'vue'
import { chartLabelStep } from '../riderReport'

const props = defineProps({
  series: { type: Array, default: () => [] },
  maxSeconds: { type: Number, default: 28800 },
  compact: { type: Boolean, default: false }
})
const w = 360
const h = computed(() => props.compact ? 72 : 120)
const pad = 26
const tip = ref(null)
const wrap = ref(null)

const points = computed(() => {
  const rows = props.series || []
  const max = Math.max(1, Number(props.maxSeconds) || 28800)
  const gap = (w - pad * 2) / Math.max(1, rows.length)
  return rows.map((r, i) => {
    const seconds = Number(r.workedSeconds) || 0
    const hh = h.value
    const bh = (Math.min(seconds, max) / max) * (hh - pad * 2)
    const bw = Math.max(8, gap * 0.58)
    return {
      ...r,
      seconds,
      hours: (seconds / 3600).toFixed(1),
      x: pad + i * gap + gap * 0.2,
      bw,
      y: hh - pad - bh,
      h: Math.max(2, bh),
      label: String(r.date || '').slice(5)
    }
  })
})

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
  <div ref="wrap" class="chart-wrap rider-work-bars" @mouseleave="tip = null">
    <svg :viewBox="`0 0 ${w} ${h}`" class="chart" :class="{ compact }">
      <rect
        v-for="p in points"
        :key="p.date"
        class="work-bar"
        :x="p.x"
        :y="p.y"
        :width="p.bw"
        :height="p.h"
        rx="4"
        @mouseenter="show(p, $event)"
        @mousemove="show(p, $event)"
      />
      <text v-for="(p, i) in points" :key="'t' + p.date" :x="p.x + p.bw / 2" :y="h - 6" text-anchor="middle" font-size="9" fill="currentColor" opacity="0.7">
        {{ axis(p, i) }}
      </text>
    </svg>
    <div v-if="tip" class="chart-tip" :style="{ left: tip.left + 'px', top: tip.top + 'px' }">
      <b>{{ tip.date }}</b>
      <div>{{ tip.hours }} 小时</div>
    </div>
  </div>
</template>

<style scoped>
.chart-wrap { position: relative; }
.chart { width: 100%; height: 120px; color: #FF6A00; }
.chart.compact { height: 72px; }
</style>
