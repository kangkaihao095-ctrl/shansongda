<script setup>
defineProps({
  title: { type: String, default: '暂无订单' },
  hint: { type: String, default: '' }
})
</script>

<template>
  <div class="empty-state">
    <div class="empty-mark" aria-hidden="true">
      <svg viewBox="0 0 88 88">
        <ellipse cx="44" cy="78" rx="22" ry="5" fill="#F0E6D4"/>
        <path d="M22 38c0-12 10-22 22-22s22 10 22 22v22H22V38z" fill="#FFE08A"/>
        <path d="M22 58h44v8a8 8 0 0 1-8 8H30a8 8 0 0 1-8-8z" fill="#FFC300"/>
        <path d="M30 28c4-8 24-8 28 0" fill="none" stroke="#E39B00" stroke-width="2.4" stroke-linecap="round"/>
        <circle cx="38" cy="48" r="3" fill="#fff"/>
        <circle cx="50" cy="48" r="3" fill="#fff"/>
        <path d="M36 56c3 3 13 3 16 0" fill="none" stroke="#C46A12" stroke-width="2" stroke-linecap="round"/>
      </svg>
    </div>
    <b>{{ title }}</b>
    <p v-if="hint" class="muted">{{ hint }}</p>
  </div>
</template>
