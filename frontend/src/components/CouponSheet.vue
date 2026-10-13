<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CouponFace from './CouponFace.vue'
import Icon from './Icon.vue'
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
const petals = computed(() => Array.from({ length: 14 }, (_, i) => i))
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
</script>

<template>
  <Teleport to="#device-portal" defer>
    <transition name="sheet">
      <div v-if="open" class="sheet-mask coupon-mask" @click.self="emit('close')">
        <div class="coupon-sheet" role="dialog" aria-modal="true" :aria-label="title">
          <div class="sheet-handle" aria-hidden="true"></div>
          <div v-if="done" class="coupon-petals" aria-hidden="true">
            <i v-for="p in petals" :key="p" :style="{ '--i': p }"></i>
          </div>
          <button class="coupon-x" type="button" aria-label="关闭" @click="emit('close')">
            <Icon name="close" :size="16" :stroke="2.4" />
          </button>
          <div class="coupon-sheet-title">{{ done ? '已放入我的优惠券' : title }}</div>
          <div class="coupon-sheet-sub">{{ done ? '可在「我的优惠券」查看与使用' : subtitle }}</div>
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
          <transition name="fade">
            <div v-if="reveal && !done" class="coupon-detail-block">
              <template v-for="(c, idx) in items" :key="'d-' + (c.code || c.id || idx)">
                <div v-if="!focus || couponNo(c) === focus" class="coupon-rule">
                  <div v-if="c.name"><b>{{ c.name }}</b></div>
                  <div v-if="expireText(c.endAt)">{{ expireText(c.endAt) }}</div>
                  <div v-if="couponNo(c)">券号 {{ couponNo(c) }}</div>
                  <div v-for="line in couponRules(c)" :key="line">· {{ line }}</div>
                </div>
              </template>
            </div>
          </transition>
          <div class="stack-2 mt-4">
            <button v-if="!done && !reveal" class="btn ghost btn-block" type="button" @click="revealAll">查看使用规则</button>
            <button v-if="!done" class="btn btn-lg btn-block" :disabled="busy || !items.length" @click="onClaim">
              <span v-if="busy" class="spinner"></span>{{ busy ? '领取中…' : '立即领取' }}
            </button>
            <button v-else class="btn btn-lg btn-block" type="button" @click="goWallet">去我的优惠券</button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>
