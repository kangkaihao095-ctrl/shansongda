<script setup>
import { computed, ref, watch } from 'vue'
import BottomSheet from './BottomSheet.vue'
import Icon from './Icon.vue'
import { LANDMARKS } from '../coupon'
import { session } from '../session'

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'apply'])

const addresses = computed(() => session.me?.addresses || [])
const lat = ref('')
const lon = ref('')
const detail = ref('')

watch(() => props.open, (on) => {
  if (!on) return
  const cur = addresses.value.find((a) => a.isDefault) || addresses.value[0]
  lat.value = cur?.lat != null ? String(cur.lat) : '31.2397'
  lon.value = cur?.lon != null ? String(cur.lon) : '121.4903'
  detail.value = cur?.detail || ''
})

function pickAddress(a) {
  emit('apply', { type: 'default', id: a.id })
}

function pickLandmark(lm) {
  emit('apply', { type: 'location', lat: lm.lat, lon: lm.lon, detail: lm.detail })
}

function applyFine() {
  const la = Number(lat.value)
  const lo = Number(lon.value)
  if (!Number.isFinite(la) || !Number.isFinite(lo)) return
  emit('apply', { type: 'location', lat: la, lon: lo, detail: detail.value || '自定义定位' })
}
</script>

<template>
  <BottomSheet :open="open" title="选择收货定位" subtitle="推荐按默认地址计算距离，超 5 公里的店铺不进入推荐" @close="emit('close')">
    <div class="kicker mb-2">已有地址</div>
    <div class="stack-2">
      <button
        v-for="a in addresses"
        :key="a.id"
        class="loc-item"
        :class="{ on: a.isDefault }"
        type="button"
        @click="pickAddress(a)"
      >
        <span class="loc-item-ico"><Icon name="pin" :size="18" /></span>
        <span class="grow">
          <b class="ellipsis">{{ a.detail }}</b>
          <small class="num">{{ a.lat?.toFixed?.(4) }}, {{ a.lon?.toFixed?.(4) }}</small>
        </span>
        <span v-if="a.isDefault" class="tag">当前</span>
        <Icon v-else name="chevron" :size="16" class="subtle" />
      </button>
    </div>
    <p v-if="!addresses.length" class="muted">还没有地址，可用下方地标快速设置。</p>

    <div class="kicker mt-5 mb-2">上海地标</div>
    <div class="loc-grid">
      <button v-for="lm in LANDMARKS" :key="lm.key" class="chip" type="button" @click="pickLandmark(lm)">{{ lm.name }}</button>
    </div>

    <div class="kicker mt-5 mb-2">微调坐标</div>
    <div class="loc-coords">
      <input v-model="lat" class="field num" placeholder="纬度" inputmode="decimal" aria-label="纬度" />
      <input v-model="lon" class="field num" placeholder="经度" inputmode="decimal" aria-label="经度" />
    </div>
    <input v-model="detail" class="field mt-2" placeholder="地址描述" aria-label="地址描述" />
    <button class="btn btn-block mt-4" type="button" @click="applyFine">保存定位</button>
  </BottomSheet>
</template>

<style scoped>
.loc-item {
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 12px 14px;
  text-align: left;
  border-radius: var(--r-md);
  background: var(--surface);
  box-shadow: var(--sh-1);
  transition: box-shadow var(--dur-2) ease, transform var(--dur-1) ease;
}
.loc-item:active { transform: scale(0.99); }
.loc-item.on { box-shadow: 0 0 0 1.5px var(--accent), var(--sh-1); }
.loc-item b { display: block; font-size: var(--fs-base); font-weight: 650; }
.loc-item small { display: block; font-size: var(--fs-xs); color: var(--text-3); margin-top: 1px; }
.loc-item-ico {
  width: 34px; height: 34px; border-radius: 10px; flex-shrink: 0;
  display: grid; place-items: center;
  background: var(--accent-soft); color: var(--accent-strong);
}
.loc-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.loc-grid .chip { padding: 0 8px; }
.loc-coords { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
</style>
