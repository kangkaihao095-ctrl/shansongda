<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import RoleBadge from '../components/RoleBadge.vue'
import CouponFace from '../components/CouponFace.vue'
import CouponSheet from '../components/CouponSheet.vue'
import LocationSheet from '../components/LocationSheet.vue'
import { APP_NAME, SLOGAN } from '../brand'
import { CATEGORIES } from '../catalog'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { loadMe, session, toast } from '../session'
import {
  loginGiftEligible,
  markLoginGiftClaimed,
  markLoginGiftDismissed,
  markLoginGiftShown,
  shanghaiDate,
  shouldSkipLoginGiftPopup
} from '../coupon'

const router = useRouter()
const merchants = ref([])
const activities = ref([])
const homeFeed = ref({ items: [], claimed: false })
const loading = ref(true)
const loadingMore = ref(false)
const page = ref(1)
const hasNext = ref(true)
const reachedEnd = ref(false)
const q = ref('')
const sentinel = ref(null)
const locOpen = ref(false)
const sheet = ref({ open: false, title: '', subtitle: '', items: [], claimed: false, action: null, scene: '', date: '' })
const sheetBusy = ref(false)
let observer
let feedTimer

const filtered = computed(() => merchants.value.filter((m) => !q.value || (m.shopName || '').includes(q.value)))
const seckill = computed(() => activities.value.find((a) => a.type === 'SECKILL'))
const seckillCover = computed(() => seckill.value?.currentSku?.imageUrl || seckill.value?.skus?.[0]?.imageUrl)
const address = computed(() => (session.me?.addresses || []).find((a) => a.isDefault) || session.me?.addresses?.[0])

async function loadPage(reset) {
  if (loadingMore.value) return
  if (!reset && (!hasNext.value || reachedEnd.value)) return
  loadingMore.value = true
  try {
    const nextPage = reset ? 1 : page.value
    const m = await api(`/api/merchants/recommend?page=${nextPage}&size=12`)
    const items = m.data?.items || (Array.isArray(m.data) ? m.data : [])
    merchants.value = reset ? items : merchants.value.concat(items)
    hasNext.value = !!m.data?.hasNext
    page.value = nextPage + 1
    if (!hasNext.value) reachedEnd.value = true
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    loadingMore.value = false
    loading.value = false
  }
}

async function loadFeed() {
  try {
    homeFeed.value = (await api('/api/coupons/home-feed')).data || { items: [] }
  } catch {
    homeFeed.value = { items: [] }
  }
}

function openSheet(title, subtitle, items, action, extra) {
  sheet.value = { open: true, title, subtitle, items: items || [], claimed: false, action, scene: extra?.scene || '', date: extra?.date || '' }
}

async function claimSheet() {
  const act = sheet.value.action
  if (!act) return
  sheetBusy.value = true
  try {
    await act()
    sheet.value.claimed = true
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    sheetBusy.value = false
  }
}

async function maybeLoginGift() {
  const userId = session.me?.userId
  if (session.me?.role !== 'USER' || userId == null) return
  const date = shanghaiDate()
  if (shouldSkipLoginGiftPopup(userId, date)) return
  markLoginGiftShown(userId, date)
  try {
    const gift = (await api('/api/coupons/login-gift')).data
    if (!loginGiftEligible(gift)) {
      if (gift?.alreadyClaimed || gift?.claimed) markLoginGiftClaimed(userId, gift.date || date)
      return
    }
    const day = gift.date || date
    openSheet(gift.title || '登录礼', gift.subtitle || '点券面或查看详情，确认后领取', gift.items, async () => {
      const res = await api('/api/coupons/login-gift', { method: 'POST' })
      markLoginGiftClaimed(userId, day)
      toast(res.data?.message || '已放入我的优惠券')
    }, { scene: 'LOGIN', date: day })
  } catch { /* 无登录礼则安静跳过 */ }
}

function closeSheet() {
  if (sheet.value.scene === 'LOGIN' && !sheet.value.claimed) {
    markLoginGiftDismissed(session.me?.userId, sheet.value.date || shanghaiDate())
  }
  sheet.value.open = false
}

function openHomeCoupons() {
  const items = homeFeed.value.items || []
  if (!items.length) return
  if (homeFeed.value.claimed) {
    router.push('/coupons')
    return
  }
  openSheet('首页优惠券', '点券面看详情，确认后领取', items, async () => {
    const res = await api('/api/coupons/home-feed/claim', { method: 'POST' })
    homeFeed.value = { ...homeFeed.value, claimed: true }
    toast(res.data?.message || '已放入我的优惠券')
    await loadFeed()
  })
}

async function applyLocation(payload) {
  try {
    if (payload.type === 'default') {
      await api(`/api/me/addresses/${payload.id}/default`, { method: 'PUT' })
    } else {
      await api('/api/me/location', {
        method: 'PUT',
        body: { lat: payload.lat, lon: payload.lon, detail: payload.detail }
      })
    }
    await loadMe()
    locOpen.value = false
    reachedEnd.value = false
    hasNext.value = true
    await loadPage(true)
    toast('定位已更新，推荐已按新地址刷新')
  } catch (e) {
    toast(e.message, 'err')
  }
}

onMounted(async () => {
  try {
    const a = await api('/api/activities')
    activities.value = a.data || []
  } catch (e) {
    toast(e.message, 'err')
  }
  await Promise.all([loadFeed(), loadPage(true), maybeLoginGift()])
  observer = new IntersectionObserver((entries) => {
    if (entries.some((en) => en.isIntersecting)) loadPage(false)
  }, { rootMargin: '120px' })
  if (sentinel.value) observer.observe(sentinel.value)
  feedTimer = setInterval(loadFeed, 60000)
})
onUnmounted(() => {
  observer?.disconnect()
  clearInterval(feedTimer)
})
</script>

<template>
  <div class="phone page">
    <header class="frost pad">
      <div class="brand-row">
        <img class="brand-mark" src="/logo.svg" :alt="APP_NAME" />
        <div>
          <div class="row">
            <b>{{ APP_NAME }}</b>
            <RoleBadge />
          </div>
          <div class="slogan">{{ SLOGAN }}</div>
        </div>
      </div>
      <button class="loc-btn" type="button" @click="locOpen = true">
        <span>📍</span>
        <span>{{ address?.detail || '请选择收货定位' }}</span>
        <span class="muted">切换</span>
      </button>
      <div class="search"><span>⌕</span><input v-model="q" placeholder="搜索商家、美食" /></div>
    </header>
    <div class="phone-body">
      <div class="pad">
        <article v-if="seckill" class="store" @click="router.push('/activities/' + seckill.id)">
          <div class="store-cover">
            <img
              class="store-cover-img"
              :src="imgSrc(seckillCover, 'fresh', 'seckill-' + (seckill.currentSku?.skuId || seckill.id), 'dish')"
              data-category="fresh"
              :data-seed="'seckill-' + (seckill.currentSku?.skuId || seckill.id)"
              alt=""
              @error="onImgError"
            />
            <div>
              <div class="pill">限时秒杀</div>
              <div style="font-size:20px;margin-top:6px">{{ seckill.currentSku?.name || seckill.name }}</div>
            </div>
          </div>
        </article>
        <div class="grid-icons" style="margin-bottom:18px">
          <button v-for="c in CATEGORIES" :key="c.key" class="icon-cell" type="button" @click="router.push('/category/' + c.key)">
            <img class="icon-ball" :src="c.image" :alt="c.name" :data-category="c.key" :data-seed="'cat-' + c.key" @error="onImgError" />
            {{ c.name }}
          </button>
        </div>
        <div v-if="homeFeed.items?.length" class="home-coupons" @click="openHomeCoupons">
          <div class="row" style="justify-content:space-between;margin-bottom:8px">
            <b>限时神券</b>
            <span class="muted">{{ homeFeed.claimed ? '已领取 · 去使用' : '点开领取' }}</span>
          </div>
          <div class="coupon-spread packed">
            <CouponFace v-for="c in homeFeed.items" :key="c.code" :coupon="c" compact mode="cover" />
          </div>
        </div>
        <h3 class="section-title">为你推荐</h3>
        <div v-if="loading">
          <div class="skel" style="height:150px;margin-bottom:12px"></div>
          <div class="skel" style="height:150px"></div>
        </div>
        <article v-for="m in filtered" :key="m.id" class="store" @click="router.push('/shops/' + m.id)">
          <div class="store-cover">
            <img
              class="store-cover-img"
              :src="imgSrc(m.coverUrl, m.category || 'food', 'shop-' + m.id, 'shop')"
              :data-seed="'shop-' + m.id"
              :data-category="m.category || 'food'"
              alt=""
              @error="onImgError"
            />
            {{ m.shopName }}
            <span v-if="m.open === false || m.onlineStatus === 'OFFLINE'" class="cover-tag">休息中</span>
          </div>
          <div class="store-body">
            <div class="row" style="justify-content:space-between">
              <b>{{ m.shopName }}</b>
              <span class="pill" :class="{ off: m.open === false || m.onlineStatus === 'OFFLINE' }">
                {{ m.open === false || m.onlineStatus === 'OFFLINE' ? '休息中' : ((m.rating || 4.8) + ' 分') }}
              </span>
            </div>
            <div class="muted">{{ m.address }}</div>
            <div class="muted" style="margin-top:6px">
              {{ m.distanceKm != null ? m.distanceKm + ' km' : '' }}
              · {{ m.promo || '配送费按距离策略结算' }} · 月售 {{ m.completedCount || 0 }}
            </div>
          </div>
        </article>
        <div ref="sentinel" style="height:8px"></div>
        <p v-if="loadingMore" class="muted" style="text-align:center">加载中…</p>
        <p v-if="reachedEnd && merchants.length" class="muted" style="text-align:center;padding:12px 0 24px">已经到底了</p>
        <p v-if="!loading && !filtered.length" class="muted">附近 {{ address?.detail || '当前定位' }} 5 公里内暂无推荐店铺，试试切换定位</p>
      </div>
    </div>
    <TabBar />
    <LocationSheet :open="locOpen" @close="locOpen = false" @apply="applyLocation" />
    <CouponSheet
      :open="sheet.open"
      :title="sheet.title"
      :subtitle="sheet.subtitle"
      :items="sheet.items"
      :busy="sheetBusy"
      :claimed="sheet.claimed"
      @claim="claimSheet"
      @close="closeSheet"
    />
  </div>
</template>
