<script setup>
import { computed } from 'vue'

const props = defineProps({
  worked: { type: Number, default: 0 },
  max: { type: Number, default: 28800 }
})
const pct = computed(() => Math.min(100, Math.round((props.worked / Math.max(1, props.max)) * 100)))
const dash = computed(() => `${(pct.value / 100) * 314} 314`)
const hours = computed(() => (props.worked / 3600).toFixed(1))
</script>

<template>
  <svg class="ring" viewBox="0 0 120 120">
    <circle cx="60" cy="60" r="50" fill="none" stroke="#F0E4D0" stroke-width="10"/>
    <circle cx="60" cy="60" r="50" fill="none" stroke="#FF6A00" stroke-width="10"
            stroke-linecap="round" transform="rotate(-90 60 60)" :stroke-dasharray="dash"/>
    <text x="60" y="56" text-anchor="middle" font-size="22" font-weight="800">{{ hours }}</text>
    <text x="60" y="76" text-anchor="middle" font-size="11" fill="#7A6A58">小时 / {{ (max/3600) }}h</text>
  </svg>
</template>
