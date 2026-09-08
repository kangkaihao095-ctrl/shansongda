<script setup>
defineProps({
  title: { type: String, default: '暂无订单' },
  hint: { type: String, default: '' }
})
</script>

<template>
  <div class="empty-state">
    <div class="empty-mark" aria-hidden="true">☰</div>
    <b>{{ title }}</b>
    <p v-if="hint" class="muted">{{ hint }}</p>
  </div>
</template>
