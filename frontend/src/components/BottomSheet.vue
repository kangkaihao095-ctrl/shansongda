<script setup>
import { onUnmounted, watch } from 'vue'
import Icon from './Icon.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' }
})
const emit = defineEmits(['close'])

function onKey(e) {
  if (e.key === 'Escape') emit('close')
}

watch(() => props.open, (v) => {
  if (v) window.addEventListener('keydown', onKey)
  else window.removeEventListener('keydown', onKey)
}, { immediate: true })
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="#device-portal" defer>
    <transition name="sheet">
      <div v-if="open" class="sheet-mask" @click.self="emit('close')">
        <div class="sheet" role="dialog" aria-modal="true" :aria-label="title || undefined">
          <div class="sheet-handle" aria-hidden="true"></div>
          <div v-if="title || $slots.head" class="sheet-head">
            <slot name="head">
              <div>
                <b>{{ title }}</b>
                <div v-if="subtitle" class="muted">{{ subtitle }}</div>
              </div>
            </slot>
            <button class="icon-btn sm plain" type="button" aria-label="关闭" @click="emit('close')">
              <Icon name="close" :size="18" />
            </button>
          </div>
          <div class="sheet-body"><slot /></div>
          <div v-if="$slots.footer" class="sheet-foot"><slot name="footer" /></div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>
