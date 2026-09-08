<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import CouponFace from '../components/CouponFace.vue'
import CouponSheet from '../components/CouponSheet.vue'
import CouponDetail from '../components/CouponDetail.vue'
import { api } from '../api'
import { goBack, session, toast } from '../session'
import { loginGiftEligible, markLoginGiftClaimed, shanghaiDate } from '../coupon'

const router = useRouter()
const tab = ref('UNUSED')
const list = ref([])
const mine = ref([])
const today = ref({ items: [], claimed: false, scene: null })
const loginGift = ref({ items: [], claimed: false, eligible: false })
const sheet = ref({ open: false, title: '', items: [], claimed: false, action: null })
const detail = ref(null)
const busy = ref(false)

const history = computed(() => mine.value.filter((c) => c.grantStatus === 'USED' || c.grantStatus === 'EXPIRED'))
const usable = computed(() => mine.value.filter((c) => c.grantStatus === 'UNUSED'))

async function load() {
  try {
    const [a, unused, hist, t, g] = await Promise.all([
      api('/api/coupons'),
      api('/api/me/coupons?status=UNUSED'),
      api('/api/me/coupons?status=EXPIRED,USED'),
      api('/api/coupons/today').catch(() => ({ data: {} })),
      api('/api/coupons/login-gift').catch(() => ({ data: {} }))
    ])
    list.value = a.data || []
    mine.value = [...(unused.data || []), ...(hist.data || [])]
    today.value = t.data || { items: [] }
    loginGift.value = g.data || { items: [] }
  } catch (e) {
    toast(e.message, 'err')
  }
}

function openClaim(title, items, action) {
  sheet.value = { open: true, title, items, claimed: false, action }
}

async function runClaim() {
  if (!sheet.value.action) return
  busy.value = true
  try {
    await sheet.value.action()
    sheet.value.claimed = true
    await load()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

function claimCatalog(c) {
  if (c.claimed) return
  openClaim(c.name || '领取优惠券', [c], async () => {
    if (c.activityId) {
      const res = await api(`/api/activities/${c.activityId}/grab-coupon`, { method: 'POST' })
      toast(res.replayed ? '已抢过该券' : '抢券成功')
    } else {
      await api(`/api/coupons/${c.id}/claim`, { method: 'POST' })
      toast('已放入我的优惠券')
    }
  })
}

function claimToday() {
  if (today.value.claimed) return
  openClaim('今日券包', today.value.items || [], async () => {
    const res = await api('/api/coupons/grant-session', { method: 'POST' })
    toast(res.data?.message || (res.data?.replayed ? '今日已领过' : '已放入我的优惠券'))
  })
}

function claimLogin() {
  if (!loginGiftEligible(loginGift.value)) return
  openClaim(loginGift.value.title || '登录礼', loginGift.value.items || [], async () => {
    const res = await api('/api/coupons/login-gift', { method: 'POST' })
    markLoginGiftClaimed(session.me?.userId, loginGift.value.date || shanghaiDate())
    toast(res.data?.message || '已放入我的优惠券')
  })
}

onMounted(load)
</script>

<template>
  <div class="phone page">
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router)">← 返回</button>
      <b>我的优惠券</b>
    </header>
    <div class="phone-body pad">
      <h3 v-if="loginGiftEligible(loginGift)">登录礼</h3>
      <article v-if="loginGiftEligible(loginGift)" class="card" style="margin-bottom:12px" @click="claimLogin()">
        <div class="coupon-spread packed">
          <CouponFace v-for="it in loginGift.items" :key="it.code" :coupon="it" compact mode="cover" />
        </div>
        <button class="btn" style="width:100%;margin-top:10px" type="button" @click.stop="claimLogin">领取登录礼</button>
      </article>
      <h3>今日可领</h3>
      <article class="card" style="margin-bottom:12px" @click="!today.claimed && today.items?.length && claimToday()">
        <div v-if="today.items?.length" class="coupon-spread packed">
          <CouponFace v-for="it in today.items" :key="it.code" :coupon="it" compact mode="cover" />
        </div>
        <p v-else class="muted">今日暂无按活跃度发放的券包</p>
        <button v-if="today.items?.length" class="btn" style="width:100%;margin-top:10px" :disabled="today.claimed" type="button" @click.stop="claimToday">
          {{ today.claimed ? '今日已领' : '领取' }}
        </button>
      </article>
      <h3>可领</h3>
      <div v-for="c in list" :key="c.id" class="coupon-row" @click="claimCatalog(c)">
        <CouponFace :coupon="c" compact mode="cover" />
        <button class="btn" :disabled="c.claimed" type="button">{{ c.claimed ? '已领取' : '领取' }}</button>
      </div>
      <p v-if="!list.length" class="muted">暂无可领优惠券</p>
      <div class="seg" style="margin-top:18px">
        <button type="button" :class="{ on: tab === 'UNUSED' }" @click="tab = 'UNUSED'">可使用 {{ usable.length }}</button>
        <button type="button" :class="{ on: tab === 'HISTORY' }" @click="tab = 'HISTORY'">已过期/已使用</button>
      </div>
      <template v-if="tab === 'UNUSED'">
        <div v-for="c in usable" :key="c.grantId" class="coupon-row" @click="detail = c">
          <CouponFace :coupon="c" compact mode="wallet" />
        </div>
        <p v-if="!usable.length" class="muted">暂无可用券</p>
      </template>
      <template v-else>
        <div v-for="c in history" :key="c.grantId" class="coupon-row dim" @click="detail = c">
          <CouponFace :coupon="c" compact mode="wallet" />
          <span class="pill off">{{ c.grantStatus === 'USED' ? '已使用' : '已过期' }}</span>
        </div>
        <p v-if="!history.length" class="muted">还没有历史券</p>
      </template>
    </div>
    <TabBar />
    <CouponSheet
      :open="sheet.open"
      :title="sheet.title"
      :items="sheet.items"
      :busy="busy"
      :claimed="sheet.claimed"
      @claim="runClaim"
      @close="sheet.open = false"
    />
    <CouponDetail :open="!!detail" :coupon="detail" @close="detail = null" />
  </div>
</template>
