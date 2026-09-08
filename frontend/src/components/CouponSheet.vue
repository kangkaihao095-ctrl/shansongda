<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CouponFace from './CouponFace.vue'
import { couponNo, couponRules, expireText } from '../coupon'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '闪送达优惠券' },
  subtitle: { type: String, default: '点券面或查看详情后再领取' },
  items: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  claimed: { type: Boolean, default: false }
})
const emit = defineEmits(['claim', 'close'])
const router = useRouter()
const petals = computed(() => Array.from({ length: 12 }, (_, i) => i))
const done = computed(() => props.claimed)
const reveal = ref(false)
const focus = ref('')

watch(() => props.open, (v) => {
  if (!v) {
    reveal.value = false
    focus.value = ''
  }
})

function faceMode(c) {
  if (reveal.value && (!focus.value || couponNo(c) === focus.value)) return 'detail'
  return 'cover'
}

function onCard(c) {
  focus.value = couponNo(c)
  reveal.value = true
}

function revealAll() {
  focus.value = ''
  reveal.value = true
}

function onClaim() {
  if (props.busy || done.value) return
  emit('claim')
}

function goWallet() {
  emit('close')
  router.push('/coupons')
}

function onClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="coupon-mask" @click.self="onClose">
      <div class="coupon-sheet" @click.stop>
        <div v-if="done" class="coupon-petals" aria-hidden="true">
          <i v-for="p in petals" :key="p" :style="{ '--i': p }"></i>
        </div>
        <button class="coupon-x" type="button" @click="onClose">×</button>
        <div class="coupon-sheet-title">{{ done ? '已放入我的优惠券' : title }}</div>
        <div class="muted" style="text-align:center;margin-bottom:12px">{{ done ? '可在「我的优惠券」查看与使用' : subtitle }}</div>
        <div class="coupon-spread" :class="{ packed: items.length === 1 }">
          <button
            v-for="(c, idx) in items"
            :key="c.code || c.couponId || c.id || idx"
            class="coupon-face-btn"
            type="button"
            @click="onCard(c)"
          >
            <CouponFace :coupon="c" :flipping="done" :mode="faceMode(c)" />
          </button>
        </div>
        <div v-if="reveal && !done" class="coupon-detail-block">
          <template v-for="(c, idx) in items" :key="'d-' + (c.code || c.id || idx)">
            <div v-if="!focus || couponNo(c) === focus" class="coupon-rule">
              <div v-if="c.name"><b>{{ c.name }}</b></div>
              <div v-if="expireText(c.endAt)">{{ expireText(c.endAt) }}</div>
              <div v-if="couponNo(c)">券号 {{ couponNo(c) }}</div>
              <div v-for="line in couponRules(c)" :key="line">{{ line }}</div>
            </div>
          </template>
        </div>
        <button v-if="!done && !reveal" class="btn ghost" style="width:100%;margin-top:12px" type="button" @click="revealAll">查看详情</button>
        <button v-if="!done" class="btn" style="width:100%;margin-top:12px" :disabled="busy || !items.length" @click="onClaim">
          {{ busy ? '领取中…' : '立即领取' }}
        </button>
        <button v-else class="btn" style="width:100%;margin-top:16px" type="button" @click="goWallet">去我的优惠券</button>
      </div>
    </div>
  </Teleport>
</template>
