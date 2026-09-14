<script setup>
import { couponFaceLabel, couponIcon, couponNo, couponRules, couponThreshold, couponTone, expireText } from '../coupon'

defineProps({
  open: { type: Boolean, default: false },
  coupon: { type: Object, default: null }
})
const emit = defineEmits(['close'])
</script>

<template>
  <Teleport to="body">
    <div v-if="open && coupon" class="coupon-mask" @click.self="emit('close')">
      <div class="coupon-sheet" @click.stop>
        <button class="coupon-x" type="button" @click="emit('close')">×</button>
        <div class="sheet-handle"></div>
        <div class="coupon-sheet-title">优惠券详情</div>
        <article class="coupon-face" :class="couponTone(coupon)" style="margin:8px 0 14px">
          <div class="coupon-face-inner">
            <img class="coupon-ico" :src="couponIcon(coupon)" alt="" />
            <div class="coupon-amt"><b>{{ couponFaceLabel(coupon) }}</b></div>
            <div class="coupon-meta">
              <div class="coupon-name">{{ coupon.name }}</div>
              <div>{{ couponThreshold(coupon) }}</div>
            </div>
          </div>
        </article>
        <div class="coupon-detail-block">
          <div class="coupon-rule">
            <div v-if="expireText(coupon.endAt)">{{ expireText(coupon.endAt) }}</div>
            <div v-if="couponNo(coupon)">券号 {{ couponNo(coupon) }}</div>
            <div class="coupon-freight">{{ coupon.coversFreight === true ? '可抵运费' : '不抵扣运费（只抵商品）' }}</div>
            <div v-for="line in couponRules(coupon)" :key="line">{{ line }}</div>
          </div>
        </div>
        <button class="btn" style="width:100%;margin-top:16px" type="button" @click="emit('close')">知道了</button>
      </div>
    </div>
  </Teleport>
</template>
