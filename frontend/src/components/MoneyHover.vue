<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { yuan, yuanExact } from '../brand'

const props = defineProps({
  cents: { type: [Number, String], default: 0 },
  label: { type: String, default: '' }
})

const shown = ref(0)
const hover = ref(false)
const pinned = ref(false)
let raf = 0
let pressTimer = 0

const exact = computed(() => yuanExact(props.cents))
const visible = computed(() => hover.value || pinned.value)

function animate(to) {
  cancelAnimationFrame(raf)
  const from = shown.value
  const target = Number(to) || 0
  const start = performance.now()
  const dur = 720
  const tick = (now) => {
    const t = Math.min(1, (now - start) / dur)
    const ease = 1 - (1 - t) ** 3
    shown.value = Math.round(from + (target - from) * ease)
    if (t < 1) raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
}

function onDown() {
  clearTimeout(pressTimer)
  pressTimer = window.setTimeout(() => { pinned.value = true }, 420)
}

function onUp() {
  clearTimeout(pressTimer)
}

function onClick() {
  pinned.value = !pinned.value
}

watch(() => Number(props.cents) || 0, (v) => animate(v), { immediate: true })
onUnmounted(() => {
  cancelAnimationFrame(raf)
  clearTimeout(pressTimer)
})
</script>

<template>
  <div
    class="money-hover"
    @mouseenter="hover = true"
    @mouseleave="hover = false"
    @pointerdown="onDown"
    @pointerup="onUp"
    @pointercancel="onUp"
    @click.stop="onClick"
  >
    <slot :text="yuan(shown)" :exact="exact">
      <span class="num">{{ yuan(shown) }}</span>
    </slot>
    <div v-if="visible" class="money-tip" role="tooltip">{{ label ? label + ' ' : '' }}{{ exact }}</div>
  </div>
</template>
