<script setup>
import { session } from './session'
import DeliveryFloat from './components/DeliveryFloat.vue'
</script>

<template>
  <div class="app-shell" :data-role="session.me?.role || 'GUEST'">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <DeliveryFloat />
    <transition name="toast">
      <div v-if="session.toast" class="toast" :class="session.toastKind">{{ session.toast }}</div>
    </transition>
  </div>
</template>
