<script setup>
defineProps({
  compact: { type: Boolean, default: false },
  label: { type: String, default: '商家备餐中' }
})
</script>

<template>
  <div class="cook-status" :class="{ compact }">
    <div class="cook-stage" aria-hidden="true">
      <span class="cook-steam s1"></span>
      <span class="cook-steam s2"></span>
      <span class="cook-steam s3"></span>
      <img src="/images/status/cooking.svg" alt="" />
    </div>
    <span class="cook-label">{{ label }}</span>
  </div>
</template>
