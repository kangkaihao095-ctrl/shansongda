<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
const canvas = ref(null)
let timer
let onResize

onMounted(() => {
  const el = canvas.value
  const ctx = el.getContext('2d')
  const dots = Array.from({ length: 16 }, () => ({
    x: Math.random(),
    y: Math.random() * 0.62,
    r: 0.4 + Math.random() * 0.9,
    s: 0.08 + Math.random() * 0.18
  }))
  onResize = () => {
    el.width = el.clientWidth * devicePixelRatio
    el.height = el.clientHeight * devicePixelRatio
  }
  onResize()
  window.addEventListener('resize', onResize)
  function draw(t) {
    if (!el.isConnected || !ctx) return
    const w = el.width
    const h = el.height
    if (!w || !h) {
      timer = requestAnimationFrame(draw)
      return
    }
    const g = ctx.createLinearGradient(0, 0, 0, h)
    g.addColorStop(0, '#1b1028')
    g.addColorStop(0.45, '#3a1830')
    g.addColorStop(1, '#ff7a1a')
    ctx.fillStyle = g
    ctx.fillRect(0, 0, w, h)
    ctx.fillStyle = 'rgba(255,230,160,.28)'
    dots.forEach((d) => {
      const x = ((d.x + t * d.s * 0.00004) % 1) * w
      ctx.beginPath()
      ctx.arc(x, d.y * h, d.r * devicePixelRatio, 0, Math.PI * 2)
      ctx.fill()
    })
    ctx.fillStyle = '#120c10'
    ctx.beginPath()
    ctx.moveTo(0, h * 0.72)
    const peaks = [0.12, 0.2, 0.16, 0.28, 0.18, 0.24, 0.14]
    peaks.forEach((p, i) => {
      const x = ((i + 1) / (peaks.length + 1)) * w
      ctx.lineTo(x, h * (0.72 - p))
    })
    ctx.lineTo(w, h * 0.72)
    ctx.lineTo(w, h)
    ctx.lineTo(0, h)
    ctx.fill()
    timer = requestAnimationFrame(draw)
  }
  timer = requestAnimationFrame(draw)
})

onUnmounted(() => {
  cancelAnimationFrame(timer)
  if (onResize) window.removeEventListener('resize', onResize)
})
</script>

<template>
  <canvas ref="canvas" class="login-bg"></canvas>
</template>
