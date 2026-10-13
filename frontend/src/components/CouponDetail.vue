<script setup>
import CouponFace from './CouponFace.vue'
import Icon from './Icon.vue'
import { couponNo, couponRules, expireText } from '../coupon'

defineProps({
  open: { type: Boolean, default: false },
  coupon: { type: Object, default: null }
})
const emit = defineEmits(['close'])
</script>

<template>
  <Teleport to="#device-portal" defer>
    <transition name="sheet">
      <div v-if="open && coupon" class="sheet-mask coupon-mask" @click.self="emit('close')">
        <div class="coupon-sheet" role="dialog" aria-modal="true" aria-label="优惠券详情">
          <div class="sheet-handle" aria-hidden="true"></div>
          <button class="coupon-x" type="button" aria-label="关闭" @click="emit('close')">
            <Icon name="close" :size="16" :stroke="2.4" />
          </button>
          <div class="coupon-sheet-title">优惠券详情</div>
          <div class="coupon-sheet-sub">{{ coupon.name }}</div>
          <CouponFace :coupon="coupon" mode="detail" />
          <div class="coupon-detail-block">
            <div class="coupon-rule">
              <div v-if="expireText(coupon.endAt)">{{ expireText(coupon.endAt) }}</div>
              <div v-if="couponNo(coupon)">券号 {{ couponNo(coupon) }}</div>
              <div class="coupon-freight">{{ coupon.coversFreight === true ? '可抵运费' : '不抵扣运费（只抵商品）' }}</div>
              <div v-for="line in couponRules(coupon)" :key="line">· {{ line }}</div>
            </div>
          </div>
          <button class="btn btn-lg btn-block mt-5" type="button" @click="emit('close')">知道了</button>
        </div>
      </div>
    </transition>
  </Teleport>
</template>
