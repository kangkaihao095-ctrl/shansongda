<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import RoleBadge from '../components/RoleBadge.vue'
import RiderCard from '../components/RiderCard.vue'
import MemberBadge from '../components/MemberBadge.vue'
import CouponSheet from '../components/CouponSheet.vue'
import LocationSheet from '../components/LocationSheet.vue'
import { APP_NAME, initials, yuan } from '../brand'
import { api, upload } from '../api'
import { onImgError } from '../img'
import { loadMe, logout, session, toast } from '../session'

const router = useRouter()
const name = ref(session.me?.displayName || '')
const riderProfile = ref(session.me?.riderProfile || null)
const member = computed(() => session.me?.member || null)
const benefits = ref(null)
const unusedCount = ref(0)
const locOpen = ref(false)
const profileOpen = ref(false)
const renaming = ref(false)
const sheet = ref({ open: false, items: [], claimed: false })
const busy = ref(false)
const fileInput = ref(null)

const identity = computed(() => {
  const me = session.me
  if (!me) return ''
  if (me.role === 'MERCHANT') return `${me.displayName} · 商家`
  if (me.role === 'RIDER') return `${me.displayName} · 骑手`
  return me.displayName || '闪送达用户'
})

const planLine = computed(() => {
  const m = member.value
  if (!m?.subscribed) return ''
  if (m.autoRenew) return '连续包月 · 到期自动续 31 天'
  return m.planLabel || ''
})

async function loadExtras() {
  if (session.me?.role === 'USER') {
    try {
      unusedCount.value = ((await api('/api/me/coupons?status=UNUSED')).data || []).length
    } catch { unusedCount.value = 0 }
    if (session.me?.member?.active) {
      try { benefits.value = (await api('/api/member/benefits')).data } catch { benefits.value = null }
    }
  }
  if (session.me?.role === 'RIDER') {
    try { riderProfile.value = (await api('/api/riders/me/profile')).data } catch {
      riderProfile.value = session.me?.riderProfile || null
    }
  }
}

function openProfile() {
  name.value = session.me?.displayName || ''
  renaming.value = false
  profileOpen.value = true
}

function closeProfile() {
  profileOpen.value = false
  renaming.value = false
}

async function saveName() {
  try {
    await api('/api/me', { method: 'PUT', body: { displayName: name.value } })
    await loadMe()
    toast('昵称已更新')
    closeProfile()
  } catch (e) { toast(e.message, 'err') }
}

function pickAvatar() {
  fileInput.value?.click()
}

async function onFile(e) {
  const f = e.target.files?.[0]
  e.target.value = ''
  if (!f) return
  try {
    await upload('/api/me/avatar', f)
    await loadMe()
    toast('头像已更新')
    closeProfile()
  } catch (err) { toast(err.message, 'err') }
}

function out() {
  logout()
  router.replace('/login')
}

function openMemberPack() {
  const items = (benefits.value?.items || []).filter((it) => !it.claimed)
  const show = items.length ? items : (benefits.value?.items || [])
  if (!show.length) {
    toast('本周红包尚未就绪', 'err')
    return
  }
  if (items.length === 0) {
    toast('本周会员红包已领过')
    return
  }
  sheet.value = { open: true, items: show, claimed: false }
}

async function claimMember() {
  busy.value = true
  try {
    const res = await api('/api/member/claim', { method: 'POST' })
    sheet.value.claimed = true
    toast(res.data?.message || '已放入我的优惠券')
    benefits.value = (await api('/api/member/benefits')).data
    await loadMe()
    await loadExtras()
  } catch (e) { toast(e.message, 'err') }
  finally { busy.value = false }
}

async function applyLocation(payload) {
  try {
    if (payload.type === 'default') {
      await api(`/api/me/addresses/${payload.id}/default`, { method: 'PUT' })
    } else {
      await api('/api/me/location', { method: 'PUT', body: { lat: payload.lat, lon: payload.lon, detail: payload.detail } })
    }
    await loadMe()
    locOpen.value = false
    toast('默认地址已更新')
  } catch (e) { toast(e.message, 'err') }
}

onMounted(loadExtras)
</script>

<template>
  <div class="phone page">
    <header class="frost pad">
      <div class="row" style="justify-content:space-between">
        <b>我的</b>
        <RoleBadge />
      </div>
    </header>
    <div class="phone-body pad">
      <div class="me-hero">
        <button class="avatar" type="button" @click="openProfile">
          <img v-if="session.me?.avatarUrl" :src="session.me.avatarUrl" alt="" data-seed="me-avatar" @error="onImgError" style="width:64px;height:64px;border-radius:50%;object-fit:cover" />
          <span v-else>{{ initials(session.me?.displayName) }}</span>
        </button>
        <div style="flex:1">
          <button class="me-name" type="button" @click="openProfile">
            <span class="row" style="gap:8px">
              <b style="font-size:18px">{{ identity }}</b>
              <MemberBadge v-if="session.me?.role === 'USER'" :member="member" />
            </span>
            <div class="muted">{{ session.me?.phone }}</div>
          </button>
        </div>
      </div>

      <div v-if="session.me?.role === 'USER'" class="member-hero" @click="member?.subscribed ? null : router.push('/member/pay')">
        <div class="row" style="justify-content:space-between">
          <div>
            <div class="member-hero-kicker">闪送达</div>
            <div class="member-hero-title">闪会员{{ member?.title ? '·' + member.title : '' }}</div>
          </div>
          <MemberBadge :member="member" />
        </div>
        <template v-if="member?.subscribed">
          <div class="muted" style="margin-top:8px">
            {{ planLine }}
            · {{ member.expired ? '已过期' : '到期 ' + String(member.expireAt || '').slice(0, 10) }}
            · 净实付 {{ yuan(member.paidCentsNet || 0) }}
          </div>
          <div class="member-bar"><i :style="{ width: (member.progressPercent || 0) + '%' }"></i></div>
          <div class="muted" style="margin-top:6px">
            <span v-if="member.nextThresholdYuan > 0">距下一级 {{ member.nextThresholdYuan }} 元</span>
            <span v-else>已满级</span>
          </div>
          <div class="row" style="margin-top:12px">
            <button v-if="member.active" class="btn" type="button" @click.stop="openMemberPack">领取本周红包</button>
            <button class="btn ghost" type="button" @click.stop="router.push('/member/pay')">续费</button>
          </div>
        </template>
        <template v-else>
          <p class="muted">开通后可领每周无门槛红包，评论展示闪会员徽章。</p>
          <button class="btn" style="width:100%" type="button">开通闪会员</button>
        </template>
      </div>

      <div v-if="session.me?.role === 'USER'" class="me-grid">
        <button class="me-cell" type="button" @click="router.push('/coupons')">
          <b>{{ unusedCount }}</b>
          <span>红包卡券</span>
        </button>
        <button class="me-cell" type="button" @click="router.push('/me/reviews')">
          <b>评</b>
          <span>我的评价</span>
        </button>
        <button class="me-cell" type="button" @click="locOpen = true">
          <b>{{ (session.me.addresses || []).length }}</b>
          <span>收货地址</span>
        </button>
        <button class="me-cell" type="button" @click="router.push('/member/pay')">
          <b>闪</b>
          <span>闪会员</span>
        </button>
      </div>

      <RiderCard v-if="session.me?.role === 'RIDER' && riderProfile" :profile="riderProfile" />
      <div v-if="session.me?.role === 'RIDER'" class="me-grid" style="margin-top:14px">
        <button class="me-cell" type="button" @click="router.push('/rider')">
          <b>接</b>
          <span>接单大厅</span>
        </button>
        <button class="me-cell" type="button" @click="router.push('/orders')">
          <b>任</b>
          <span>我的任务</span>
        </button>
      </div>

      <div v-if="session.me?.role === 'MERCHANT'" class="me-grid">
        <button class="me-cell" type="button" @click="router.push('/shop')">
          <b>店</b>
          <span>店铺经营</span>
        </button>
        <button class="me-cell" type="button" @click="router.push('/orders')">
          <b>单</b>
          <span>订单中心</span>
        </button>
      </div>

      <p class="muted" style="margin-top:16px;text-align:center">{{ APP_NAME }} · {{ identity }}</p>
      <button class="btn ghost" style="width:100%;margin-top:18px" @click="out">退出登录</button>
    </div>
    <TabBar />
    <input ref="fileInput" type="file" accept="image/*" hidden @change="onFile" />
    <Teleport to="body">
      <div v-if="profileOpen" class="coupon-mask" @click.self="closeProfile">
        <div class="loc-sheet profile-sheet" @click.stop>
          <template v-if="!renaming">
            <b>账号</b>
            <button class="profile-action" type="button" @click="renaming = true">改昵称</button>
            <button class="profile-action" type="button" @click="pickAvatar">换头像</button>
            <button class="profile-action ghost" type="button" @click="closeProfile">取消</button>
          </template>
          <template v-else>
            <b>改昵称</b>
            <input v-model="name" style="width:100%;border:1px solid var(--line);border-radius:12px;padding:10px;margin:12px 0" />
            <div class="row">
              <button class="btn" type="button" @click="saveName">保存</button>
              <button class="btn ghost" type="button" @click="renaming = false">取消</button>
            </div>
          </template>
        </div>
      </div>
    </Teleport>
    <LocationSheet :open="locOpen" @close="locOpen = false" @apply="applyLocation" />
    <CouponSheet
      :open="sheet.open"
      title="本周闪会员红包"
      subtitle="金底券面，确认后领取"
      :items="sheet.items"
      :busy="busy"
      :claimed="sheet.claimed"
      @claim="claimMember"
      @close="sheet.open = false"
    />
  </div>
</template>
