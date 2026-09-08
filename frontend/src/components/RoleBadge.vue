<script setup>
import { computed } from 'vue'
import { session } from '../session'

const props = defineProps({
  label: { type: String, default: '' }
})

const text = computed(() => {
  if (props.label) return props.label
  const role = session.me?.role
  if (role === 'RIDER') return '骑手'
  if (role === 'MERCHANT') return '商家'
  if (role === 'USER') return '外卖'
  return ''
})
</script>

<template>
  <span v-if="text" class="role-badge">{{ text }}</span>
</template>
