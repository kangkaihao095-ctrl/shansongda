<script setup>
const props = defineProps({
  modelValue: { type: Number, default: 0 },
  size: { type: String, default: 'lg' },
  readonly: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

function pick(n) {
  if (props.readonly) return
  emit('update:modelValue', n)
}
</script>

<template>
  <div class="star-row" :class="'star-' + size" role="radiogroup">
    <button
      v-for="n in 5"
      :key="n"
      type="button"
      class="star-btn"
      :class="{ on: n <= modelValue }"
      role="radio"
      :aria-checked="n <= modelValue"
      :aria-label="n + ' 星'"
      :disabled="readonly"
      @click="pick(n)"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 2.4l2.7 5.5 6.1.9-4.4 4.3 1 6.1L12 16.3 6.6 19.2l1-6.1L3.2 8.8l6.1-.9L12 2.4z" />
      </svg>
    </button>
  </div>
</template>
