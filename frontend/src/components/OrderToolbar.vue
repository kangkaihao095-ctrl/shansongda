<script setup>
import { PAGE_SIZES } from '../orderQuery'

defineProps({
  q: { type: String, default: '' },
  size: { type: Number, default: 10 },
  page: { type: Number, default: 1 },
  pages: { type: Number, default: 1 },
  hasNext: { type: Boolean, default: false },
  hasPrev: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  placeholder: { type: String, default: '搜索订单号、状态、店铺、商品、地址' }
})

const emit = defineEmits(['update:q', 'size', 'prev', 'next'])
</script>

<template>
  <div class="order-toolbar">
    <label class="search order-search">
      <span>⌕</span>
      <input
        :value="q"
        :placeholder="placeholder"
        @input="emit('update:q', $event.target.value)"
      />
    </label>
    <div class="seg size-seg">
      <button
        v-for="n in PAGE_SIZES"
        :key="n"
        type="button"
        :class="{ on: size === n }"
        @click="emit('size', n)"
      >{{ n }} 条</button>
    </div>
    <div class="pager">
      <button type="button" class="btn ghost pager-btn" :disabled="!hasPrev" @click="emit('prev')">上一页</button>
      <span class="muted pager-ind">第 {{ page }} / {{ pages }} 页</span>
      <button type="button" class="btn ghost pager-btn" :disabled="!hasNext" @click="emit('next')">下一页</button>
    </div>
  </div>
</template>
