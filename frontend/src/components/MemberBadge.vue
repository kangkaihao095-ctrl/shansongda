<script setup>
import { computed } from 'vue'

const props = defineProps({
  member: { type: Object, default: null },
  compact: { type: Boolean, default: false }
})

const visible = computed(() => props.member && (props.member.subscribed || props.member.level))
const title = computed(() => {
  if (props.member?.expired) return '已过期'
  return props.member?.title || ''
})
const label = computed(() => {
  if (props.member?.expired) return '闪会员'
  if (props.compact) return '闪会员'
  if (title.value) return `闪会员·${title.value}`
  return `闪会员 Lv${props.member?.level || 1}`
})
</script>

<template>
  <span
    v-if="visible"
    class="member-badge"
    :class="{ compact, year: member.yearMember && !member.expired, expired: member.expired }"
    :title="member.expired ? '闪会员已过期' : label"
  >
    <span class="member-shine" aria-hidden="true"></span>
    <span v-if="member.yearMember && !member.expired && !compact" class="member-spark" aria-hidden="true"></span>
    <span class="member-lv">Lv{{ member.level || 1 }}</span>
    <span class="member-title">{{ label }}</span>
    <span v-if="member.yearMember" class="member-year">年</span>
  </span>
</template>
