<script setup>
import { computed, watch } from 'vue'
import { session } from './session'
import DeliveryFloat from './components/DeliveryFloat.vue'
import Icon from './components/Icon.vue'
import StageBrand from './components/stage/StageBrand.vue'
import StageTelemetry from './components/stage/StageTelemetry.vue'
import { bootRiderLive, stopRiderLive } from './riderLive'

watch(() => session.me?.role, (role) => {
  if (role === 'RIDER') bootRiderLive()
  else stopRiderLive()
}, { immediate: true })

const theme = computed(() => {
  const role = session.me?.role
  if (role === 'RIDER') return 'rider'
  if (role === 'MERCHANT') return 'merchant'
  return 'user'
})
</script>

<template>
  <div class="app-shell" :data-role="session.me?.role || 'GUEST'" :data-theme="theme">
    <div class="stage-bg" aria-hidden="true">
      <i class="stage-orb o1"></i>
      <i class="stage-orb o2"></i>
      <i class="stage-grid"></i>
    </div>
    <StageBrand class="stage-aside stage-left" />
    <main class="device-frame">
      <div class="device" id="device">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
        <DeliveryFloat />
        <transition name="toast">
          <div v-if="session.toast" class="toast" :class="session.toastKind" role="status" aria-live="polite">
            <span class="toast-ico"><Icon :name="session.toastKind === 'err' ? 'close' : 'check'" :size="12" :stroke="3" /></span>
            {{ session.toast }}
          </div>
        </transition>
        <div id="device-portal" class="device-portal"></div>
      </div>
    </main>
    <StageTelemetry class="stage-aside stage-right" />
  </div>
</template>
