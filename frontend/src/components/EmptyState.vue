<script setup>
defineProps({
  title: { type: String, default: '暂无订单' },
  hint: { type: String, default: '' },
  /** empty | error | search | offline */
  kind: { type: String, default: 'empty' },
  bare: { type: Boolean, default: false }
})
</script>

<template>
  <div class="empty-state" :class="{ bare }" role="status">
    <div class="empty-mark" aria-hidden="true">
      <svg v-if="kind === 'error'" viewBox="0 0 104 104">
        <circle cx="52" cy="52" r="40" fill="var(--danger-50)"/>
        <path d="M52 30 30 70h44z" fill="#fff" stroke="var(--danger-500)" stroke-width="3" stroke-linejoin="round"/>
        <path d="M52 46v10M52 62.5v.5" stroke="var(--danger-500)" stroke-width="3.4" stroke-linecap="round"/>
      </svg>
      <svg v-else-if="kind === 'search'" viewBox="0 0 104 104">
        <circle cx="52" cy="52" r="40" fill="var(--accent-soft)"/>
        <circle cx="48" cy="48" r="16" fill="#fff" stroke="var(--accent)" stroke-width="3.4"/>
        <path d="m60 60 12 12" stroke="var(--accent)" stroke-width="4" stroke-linecap="round"/>
        <path d="M42 48h12" stroke="var(--accent-soft-2)" stroke-width="3" stroke-linecap="round"/>
      </svg>
      <svg v-else-if="kind === 'offline'" viewBox="0 0 104 104">
        <circle cx="52" cy="52" r="40" fill="var(--ink-100)"/>
        <path d="M34 66V44l18-12 18 12v22z" fill="#fff" stroke="var(--ink-400)" stroke-width="3" stroke-linejoin="round"/>
        <path d="M46 66V54h12v12" stroke="var(--ink-400)" stroke-width="3" stroke-linejoin="round" fill="none"/>
        <path d="M70 30l6-6M76 34h6" stroke="var(--ink-300)" stroke-width="3" stroke-linecap="round"/>
      </svg>
      <svg v-else viewBox="0 0 104 104">
        <circle cx="52" cy="52" r="40" fill="var(--accent-soft)"/>
        <ellipse cx="52" cy="80" rx="20" ry="3.5" fill="var(--accent-soft-2)"/>
        <path d="M33 42h38l-3 32H36z" fill="#fff" stroke="var(--accent)" stroke-width="3" stroke-linejoin="round"/>
        <path d="M43 42v-4a9 9 0 0 1 18 0v4" fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round"/>
        <path d="M54 50l-7 10h6l-2 8 8-11h-6z" fill="var(--accent)"/>
      </svg>
    </div>
    <b>{{ title }}</b>
    <p v-if="hint" class="muted">{{ hint }}</p>
    <slot />
  </div>
</template>
