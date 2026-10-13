<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, required: true },
  label: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const index = computed(() => Math.max(0, props.options.findIndex((o) => o.key === props.modelValue)))

function pick(key) {
  if (key === props.modelValue) return
  emit('update:modelValue', key)
  emit('change', key)
}
</script>

<template>
  <div class="seg" role="tablist" :aria-label="label || undefined">
    <i class="seg-thumb" :style="{ '--i': index, '--n': options.length }" aria-hidden="true"></i>
    <button
      v-for="o in options"
      :key="o.key"
      type="button"
      role="tab"
      :aria-selected="o.key === modelValue"
      :class="{ on: o.key === modelValue }"
      @click="pick(o.key)"
    >{{ o.label }}</button>
  </div>
</template>
