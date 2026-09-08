<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { session } from '../session'

const route = useRoute()
const role = computed(() => session.me?.role)
const items = computed(() => {
  if (role.value === 'RIDER') return [
    { to: '/rider', label: '大厅' },
    { to: '/orders', label: '任务' },
    { to: '/me', label: '我的' }
  ]
  if (role.value === 'MERCHANT') return [
    { to: '/shop', label: '经营' },
    { to: '/orders', label: '订单' },
    { to: '/me', label: '我的' }
  ]
  return [
    { to: '/home', label: '首页' },
    { to: '/orders', label: '订单' },
    { to: '/me', label: '我的' }
  ]
})
</script>

<template>
  <nav class="tabbar">
    <router-link v-for="it in items" :key="it.to" :to="it.to" :class="{ active: route.path.startsWith(it.to) }">
      {{ it.label }}
    </router-link>
  </nav>
</template>
