<script setup>
import Icon from './Icon.vue'
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
    <label class="search flat">
      <Icon name="search" :size="18" />
      <input
        :value="q"
        type="search"
        :placeholder="placeholder"
        :aria-label="placeholder"
        @input="emit('update:q', $event.target.value)"
      />
      <button v-if="q" class="icon-btn sm plain" type="button" aria-label="清空搜索" @click="emit('update:q', '')">
        <Icon name="close" :size="14" />
      </button>
    </label>
    <div class="ot-row">
      <div class="ot-size" role="group" aria-label="每页条数">
        <button
          v-for="n in PAGE_SIZES"
          :key="n"
          type="button"
          :class="{ on: size === n }"
          :aria-pressed="size === n"
          @click="emit('size', n)"
        >{{ n }}</button>
        <span>条/页</span>
      </div>
      <div class="ot-pager">
        <button type="button" class="icon-btn sm" :disabled="!hasPrev" aria-label="上一页" @click="emit('prev')">
          <Icon name="back" :size="16" :stroke="2.2" />
        </button>
        <span class="num">{{ page }} / {{ pages }}</span>
        <button type="button" class="icon-btn sm" :disabled="!hasNext" aria-label="下一页" @click="emit('next')">
          <Icon name="chevron" :size="16" :stroke="2.2" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-toolbar { display: flex; flex-direction: column; gap: 10px; margin-bottom: var(--s-3); }
.order-toolbar .search { height: 42px; background: var(--surface); }
.ot-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ot-size { display: inline-flex; align-items: center; gap: 4px; font-size: var(--fs-xs); color: var(--text-3); }
.ot-size button {
  min-width: 32px; height: 28px; padding: 0 8px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs); font-weight: 700; color: var(--text-2);
  background: var(--surface); box-shadow: inset 0 0 0 1px var(--line);
  font-family: var(--font-num);
  transition: background var(--dur-2) ease, color var(--dur-2) ease;
}
.ot-size button.on { background: var(--ink-900); color: #fff; box-shadow: none; }
.ot-pager { display: inline-flex; align-items: center; gap: 8px; font-size: var(--fs-sm); font-weight: 700; color: var(--text-2); }
.ot-pager .icon-btn:disabled { opacity: 0.35; box-shadow: none; }
</style>
