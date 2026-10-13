<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  length: { type: Number, default: 6 },
  label: { type: String, default: '支付密码' },
  tone: { type: String, default: '' },
  shake: { type: Boolean, default: false }
})
</script>

<template>
  <div class="pin" :class="[tone, { shake }]" role="group" :aria-label="label">
    <span
      v-for="i in length"
      :key="i"
      class="pin-cell"
      :class="{ filled: i <= modelValue.length, active: !shake && modelValue.length < length && i === modelValue.length + 1 }"
      aria-hidden="true"
    ><i></i></span>
  </div>
</template>

<style scoped>
.pin { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; }
.pin-cell {
  height: 52px;
  display: grid; place-items: center;
  border-radius: var(--r-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--line-strong);
  transition: box-shadow var(--dur-2) ease, background var(--dur-2) ease;
}
.pin-cell i {
  width: 10px; height: 10px; border-radius: 50%;
  background: var(--text);
  transform: scale(0);
  transition: transform var(--dur-3) var(--ease-spring);
}
.pin-cell.filled i { transform: scale(1); }
.pin-cell.active { background: var(--surface); }
.pin.wechat .pin-cell.active { box-shadow: inset 0 0 0 2px #07C160, 0 0 0 4px rgba(7, 193, 96, 0.16); }
.pin.alipay .pin-cell.active { box-shadow: inset 0 0 0 2px #1677FF, 0 0 0 4px rgba(22, 119, 255, 0.16); }
.pin.shake .pin-cell {
  background: var(--danger-50);
  box-shadow: inset 0 0 0 1.5px var(--danger-500);
}
.pin.shake .pin-cell i { animation: dot-jitter 520ms ease; }
@keyframes dot-jitter {
  0%, 100% { transform: translateX(0) scale(1); }
  12% { transform: translateX(-5px) scale(1); }
  24% { transform: translateX(5px) scale(1); }
  36% { transform: translateX(-4px) scale(1); }
  48% { transform: translateX(4px) scale(1); }
  62% { transform: translateX(-2px) scale(1); }
  78% { transform: translateX(2px) scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .pin.shake .pin-cell i { animation: none !important; }
}
</style>
