<script setup>
import { useRouter } from 'vue-router'
import Icon from './Icon.vue'
import { goBack } from '../session'

const props = defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  /** true 显示返回；字符串为无历史时的兜底路由 */
  back: { type: [Boolean, String], default: false },
  overlay: { type: Boolean, default: false },
  solid: { type: Boolean, default: false }
})

const router = useRouter()

function onBack() {
  goBack(router, typeof props.back === 'string' ? props.back : undefined)
}
</script>

<template>
  <header class="app-header" :class="{ 'is-overlay': overlay, 'is-solid': solid }">
    <button v-if="back" class="icon-btn" type="button" aria-label="返回" @click="onBack">
      <Icon name="back" :size="20" :stroke="2.2" />
    </button>
    <div class="header-title">
      <slot name="title">
        <b v-if="title">{{ title }}</b>
        <span v-if="subtitle">{{ subtitle }}</span>
      </slot>
    </div>
    <slot name="right" />
  </header>
</template>
