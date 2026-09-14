<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { session } from '../session'

const route = useRoute()
const role = computed(() => session.me?.role)
const items = computed(() => {
  if (role.value === 'RIDER') return [
    { to: '/rider', label: '大厅', icon: 'hall', exact: true },
    { to: '/orders', label: '任务', icon: 'list' },
    { to: '/rider/income', label: '收入', icon: 'income' },
    { to: '/me', label: '我的', icon: 'me' }
  ]
  if (role.value === 'MERCHANT') return [
    { to: '/shop', label: '经营', icon: 'shop' },
    { to: '/orders', label: '订单', icon: 'list' },
    { to: '/me', label: '我的', icon: 'me' }
  ]
  return [
    { to: '/home', label: '首页', icon: 'home' },
    { to: '/orders', label: '订单', icon: 'list' },
    { to: '/me', label: '我的', icon: 'me' }
  ]
})

function isActive(it) {
  const path = route.path
  if (it.exact) return path === it.to
  return path === it.to || path.startsWith(it.to + '/')
}
</script>

<template>
  <nav class="tabbar" :class="{ 'tabs-4': items.length === 4 }">
    <router-link v-for="it in items" :key="it.to" :to="it.to" :class="{ active: isActive(it) }">
      <span class="tab-ico" aria-hidden="true">
        <svg v-if="it.icon === 'home'" viewBox="0 0 24 24"><path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z"/></svg>
        <svg v-else-if="it.icon === 'hall'" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M12 4v2M12 18v2M4 12h2M18 12h2M6.2 6.2l1.4 1.4M16.4 16.4l1.4 1.4M6.2 17.8l1.4-1.4M16.4 7.6l1.4-1.4"/></svg>
        <svg v-else-if="it.icon === 'shop'" viewBox="0 0 24 24"><path d="M4 10h16v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z"/><path d="M4 7h16l-1.2-3H5.2z"/><path d="M9 20v-6h6v6"/></svg>
        <svg v-else-if="it.icon === 'list'" viewBox="0 0 24 24"><path d="M8 7h12M8 12h12M8 17h12"/><circle cx="4.5" cy="7" r="1.2"/><circle cx="4.5" cy="12" r="1.2"/><circle cx="4.5" cy="17" r="1.2"/></svg>
        <svg v-else-if="it.icon === 'income'" viewBox="0 0 24 24"><path d="M5 19V11M10 19V6M15 19v-8M20 19V9"/></svg>
        <svg v-else viewBox="0 0 24 24"><circle cx="12" cy="8" r="3.2"/><path d="M5 19c1.4-3.2 3.8-4.8 7-4.8s5.6 1.6 7 4.8"/></svg>
      </span>
      {{ it.label }}
    </router-link>
  </nav>
</template>
