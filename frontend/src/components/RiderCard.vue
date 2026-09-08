<script setup>
import { computed } from 'vue'
import { initials, yuan } from '../brand'
import { onImgError } from '../img'

const props = defineProps({
  profile: { type: Object, default: () => ({}) },
  compact: { type: Boolean, default: false }
})

const years = computed(() => {
  const y = Number(props.profile.yearsOnJob) || 0
  const d = Number(props.profile.daysOnJob) || 0
  if (y >= 1) return `接单 ${y} 年 · ${d} 天`
  return `送餐 ${d} 天`
})

const rateText = computed(() => {
  const r = Number(props.profile.onTimeRate)
  if (!Number.isFinite(r)) return '—'
  return `${Math.round(r * 1000) / 10}%`
})
</script>

<template>
  <section class="rider-card" :class="{ compact }">
    <div class="rider-card-head">
      <div class="avatar">
        <img
          v-if="profile.avatarUrl"
          :src="profile.avatarUrl"
          alt=""
          :data-seed="'rider-' + profile.riderId"
          @error="onImgError"
          style="width:72px;height:72px;border-radius:50%;object-fit:cover"
        />
        <span v-else>{{ initials(profile.displayName) }}</span>
      </div>
      <div style="flex:1">
        <div class="row" style="justify-content:space-between">
          <b>{{ profile.displayName || '闪送达骑手' }}</b>
          <span class="pill">准时 {{ rateText }}</span>
        </div>
        <div class="muted">{{ years }}</div>
        <div v-if="!compact" class="muted" style="margin-top:4px">累计打赏 {{ yuan(profile.tipCentsTotal) }}</div>
      </div>
    </div>
    <p class="rider-bio">{{ profile.bio }}</p>
    <p v-if="!compact" class="muted" style="font-size:12px">{{ profile.onTimeRateNote }}</p>
    <div v-if="compact" class="muted">累计打赏 {{ yuan(profile.tipCentsTotal) }}</div>
    <div class="badge-wall">
      <span
        v-for="b in (profile.badges || [])"
        :key="b.code"
        class="medal"
        :class="{ on: b.earned }"
        :title="b.hint"
      >{{ b.name }}</span>
    </div>
  </section>
</template>
