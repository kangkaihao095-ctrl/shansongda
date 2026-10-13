<script setup>
import { computed } from 'vue'

const props = defineProps({
  worked: { type: Number, default: 0 },
  max: { type: Number, default: 28800 },
  online: { type: Boolean, default: false },
  remainLabel: { type: String, default: '' },
  compact: { type: Boolean, default: false }
})
const C = 2 * Math.PI * 50
const pct = computed(() => Math.min(100, Math.round((props.worked / Math.max(1, props.max)) * 100)))
const dash = computed(() => `${(pct.value / 100) * C} ${C}`)
const hours = computed(() => (props.worked / 3600).toFixed(1))
</script>

<template>
  <svg class="ring" :class="{ 'ring-live': online, compact }" viewBox="0 0 120 120" role="img" :aria-label="`今日已在线 ${hours} 小时，上限 ${max / 3600} 小时`">
    <defs>
      <linearGradient id="ring-grad" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" class="ring-stop-a" />
        <stop offset="100%" class="ring-stop-b" />
      </linearGradient>
    </defs>
    <circle cx="60" cy="60" r="50" fill="none" class="ring-track" stroke-width="10" />
    <circle
      class="ring-progress"
      cx="60" cy="60" r="50" fill="none" stroke="url(#ring-grad)" stroke-width="10"
      stroke-linecap="round" transform="rotate(-90 60 60)" :stroke-dasharray="dash"
    />
    <text x="60" y="56" text-anchor="middle" class="ring-num">{{ hours }}</text>
    <text x="60" y="74" text-anchor="middle" class="ring-unit">h / {{ max / 3600 }}h</text>
  </svg>
</template>

<style scoped>
.ring { width: 124px; height: 124px; flex-shrink: 0; overflow: visible; }
.ring.compact { width: 56px; height: 56px; }
.ring-track { stroke: var(--ink-150); }
.ring-stop-a { stop-color: #2DD4BF; }
.ring-stop-b { stop-color: var(--accent-strong); }
.ring-progress { transition: stroke-dasharray 0.9s var(--ease-out); filter: drop-shadow(0 4px 8px var(--accent-glow)); }
.ring-num { font-family: var(--font-num); font-size: 24px; font-weight: 800; fill: var(--text); letter-spacing: -0.02em; }
.ring-unit { font-size: 10px; font-weight: 600; fill: var(--text-3); }
.ring-live { animation: ring-breathe 3s ease-in-out infinite; transform-origin: center; }
@keyframes ring-breathe { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.03); } }
</style>
