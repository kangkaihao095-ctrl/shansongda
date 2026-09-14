<script setup>
import { computed, ref } from 'vue'
import { yuanExact } from '../brand'
import { chartLabelStep } from '../riderReport'

const props = defineProps({
  series: { type: Array, default: () => [] },
  grain: { type: String, default: 'day' }
})
const w = 360
const h = 160
const pad = 24
const tip = ref(null)
const wrap = ref(null)

const points = computed(() => {
  const rows = props.series || []
  if (!rows.length) return []
  const max = Math.max(1, ...rows.map((r) => Number(r.gmvCents) || 0))
  return rows.map((r, i) => {
    const x = pad + (i * (w - pad * 2)) / Math.max(1, rows.length - 1)
    const y = h - pad - ((Number(r.gmvCents) || 0) / max) * (h - pad * 2)
    return {
      ...r,
      x,
      y,
      label: props.grain === 'month' || String(r.date || '').length === 7
        ? String(r.date || '').slice(5)
        : String(r.date || '').slice(5)
    }
  })
})

const path = computed(() => points.value.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' '))
const area = computed(() => path.value ? `${path.value} L${w - pad},${h - pad} L${pad},${h - pad} Z` : '')
const chartKey = computed(() => (props.series || []).map((r) => r.date + r.gmvCents).join('|'))

function label(p, i) {
  const n = points.value.length
  const step = chartLabelStep(n)
  if (i !== 0 && i !== n - 1 && i % step !== 0) return ''
  return p.label
}

function showTip(p, e) {
  const rect = wrap.value?.getBoundingClientRect()
  tip.value = {
    ...p,
    left: rect ? e.clientX - rect.left : p.x,
    top: rect ? e.clientY - rect.top : p.y
  }
}
</script>

<template>
  <div ref="wrap" class="chart-wrap" @mouseleave="tip = null">
    <svg :viewBox="`0 0 ${w} ${h}`" class="chart">
      <defs>
        <linearGradient id="gmv-area" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="currentColor" stop-opacity="0.42"/>
          <stop offset="100%" stop-color="currentColor" stop-opacity="0.03"/>
        </linearGradient>
      </defs>
      <g opacity="0.12">
        <line v-for="n in 4" :key="'g' + n" x1="24" :x2="336" :y1="24 + n * 28" :y2="24 + n * 28" stroke="currentColor" stroke-width="1"/>
      </g>
      <g :key="chartKey">
        <path class="chart-area" :d="area" fill="url(#gmv-area)"/>
        <path class="chart-line" :d="path" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
      </g>
      <circle
        v-for="p in points"
        :key="p.date"
        :cx="p.x"
        :cy="p.y"
        r="5"
        fill="currentColor"
        class="chart-dot"
        @mouseenter="showTip(p, $event)"
        @mousemove="showTip(p, $event)"
        @click="showTip(p, $event)"
      />
      <text
        v-for="(p, i) in points"
        :key="'t' + p.date"
        :x="p.x"
        :y="h - 6"
        text-anchor="end"
        font-size="9"
        fill="currentColor"
        opacity="0.7"
        :transform="points.length > 12 ? `rotate(-38 ${p.x} ${h - 6})` : undefined"
      >
        {{ label(p, i) }}
      </text>
    </svg>
    <div v-if="tip" class="chart-tip" :style="{ left: tip.left + 'px', top: tip.top + 'px' }">
      <b>{{ tip.date }}</b>
      <div>{{ tip.orderCount || 0 }} 单 · {{ yuanExact(tip.gmvCents) }}</div>
    </div>
  </div>
</template>

<style scoped>
.chart-wrap { position: relative; }
.chart { width: 100%; height: 176px; color: #FF6A00; }
.chart-line { stroke-dasharray: 720; stroke-dashoffset: 720; animation: draw-line .55s ease forwards; }
.chart-area { opacity: 0; animation: fade-area .45s .1s ease forwards; }
.chart-dot { cursor: pointer; }
.chart-tip {
  position: absolute; transform: translate(-50%, -120%);
  background: #1A1208; color: #fff; border-radius: 10px; padding: 6px 10px;
  font-size: 12px; pointer-events: none; white-space: nowrap; z-index: 3;
}
@keyframes draw-line { to { stroke-dashoffset: 0; } }
@keyframes fade-area { to { opacity: 1; } }
:global(.theme-rider) .chart { color: #1AA7B8; }
:global(.theme-merchant) .chart { color: #0F6E64; }
:global([data-role="MERCHANT"]) .chart { color: #0F6E64; }
:global([data-role="MERCHANT"]) .chart-tip { background: #0F3D38; }
</style>
