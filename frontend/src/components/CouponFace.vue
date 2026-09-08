<script setup>
import { couponFaceLabel, couponIcon, couponNo, couponThreshold, couponTone, expireText } from '../coupon'

defineProps({
  coupon: { type: Object, required: true },
  flipping: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  /** cover=只面额；wallet=面额+门槛一句；detail=含到期/券号 */
  mode: { type: String, default: 'cover' }
})
</script>

<template>
  <article class="coupon-face" :class="[couponTone(coupon), { flip: flipping, compact, cover: mode === 'cover', wallet: mode === 'wallet' }]">
    <div class="coupon-face-inner">
      <img class="coupon-ico" :src="couponIcon(coupon)" alt="" />
      <div class="coupon-amt">
        <b>{{ couponFaceLabel(coupon) }}</b>
      </div>
      <div v-if="mode === 'wallet'" class="coupon-meta">
        <div>{{ couponThreshold(coupon) }}</div>
      </div>
      <div v-if="mode === 'detail'" class="coupon-meta">
        <div class="coupon-name">{{ coupon.name }}</div>
        <div>{{ couponThreshold(coupon) }}</div>
        <div v-if="coupon.endAt">{{ expireText(coupon.endAt) }}</div>
        <div v-if="couponNo(coupon)">券号 {{ couponNo(coupon) }}</div>
      </div>
    </div>
  </article>
</template>
