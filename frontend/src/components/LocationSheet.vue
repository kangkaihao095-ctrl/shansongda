<script setup>
import { computed, ref, watch } from 'vue'
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
  <Teleport to="body">
    <div v-if="open" class="coupon-mask" @click.self="emit('close')">
      <div class="loc-sheet" @click.stop>
        <div class="row" style="justify-content:space-between">
          <b>选择收货定位</b>
          <button class="back-btn" type="button" @click="emit('close')">关闭</button>
        </div>
        <p class="muted">推荐按默认地址算距离，超 5 公里的店不会出现在「为你推荐」。</p>
        <h4>已有地址</h4>
        <button
          v-for="a in addresses"
          :key="a.id"
          class="loc-item"
          :class="{ on: a.isDefault }"
          type="button"
          @click="pickAddress(a)"
        >
          <b>{{ a.detail }}</b>
          <div class="muted">{{ a.isDefault ? '当前默认' : '设为默认' }} · {{ a.lat?.toFixed?.(4) }} , {{ a.lon?.toFixed?.(4) }}</div>
        </button>
        <p v-if="!addresses.length" class="muted">还没有地址，可用下方地标快速设置。</p>
        <h4>上海地标</h4>
        <div class="loc-grid">
          <button v-for="lm in LANDMARKS" :key="lm.key" class="loc-chip" type="button" @click="pickLandmark(lm)">{{ lm.name }}</button>
        </div>
        <h4>微调坐标</h4>
        <div class="row" style="margin-bottom:8px">
          <input v-model="lat" placeholder="纬度" style="flex:1;border:1px solid var(--line);border-radius:12px;padding:10px" />
          <input v-model="lon" placeholder="经度" style="flex:1;border:1px solid var(--line);border-radius:12px;padding:10px" />
        </div>
        <input v-model="detail" placeholder="地址描述" style="width:100%;border:1px solid var(--line);border-radius:12px;padding:10px;margin-bottom:10px" />
        <button class="btn" style="width:100%" type="button" @click="applyFine">保存定位</button>
      </div>
    </div>
  </Teleport>
</template>
