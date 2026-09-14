<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import CouponFace from '../components/CouponFace.vue'
import CouponSheet from '../components/CouponSheet.vue'
import LocationSheet from '../components/LocationSheet.vue'
import EmptyState from '../components/EmptyState.vue'
import SsdMap from '../components/SsdMap.vue'
import { CATEGORIES } from '../catalog'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { shopIsOpen } from '../merchant'
import { loadMe, session, toast } from '../session'
import {
  loginGiftEligible,
  markLoginGiftClaimed,
  markLoginGiftDismissed,
  markLoginGiftShown,
  shanghaiDate,
  shouldSkipLoginGiftPopup
} from '../coupon'

const route = useRoute()
const router = useRouter()
const merchants = ref([])
const activities = ref([])
const homeFeed = ref({ items: [], claimed: false })
const loading = ref(true)
const loadingMore = ref(false)
const page = ref(1)
const hasNext = ref(true)
const reachedEnd = ref(false)
const draft = ref('')
const q = ref('')
const searching = computed(() => q.value.trim().length > 0)
const sentinel = ref(null)
const locOpen = ref(false)
const nearbyRiders = ref([])
const sheet = ref({ open: false, title: '', subtitle: '', items: [], claimed: false, action: null, scene: '', date: '' })
const sheetBusy = ref(false)
let observer
let feedTimer

const seckill = computed(() => activities.value.find((a) => a.type === 'SECKILL'))
const seckillCover = computed(() => seckill.value?.currentSku?.imageUrl || seckill.value?.skus?.[0]?.imageUrl)
const address = computed(() => (session.me?.addresses || []).find((a) => a.isDefault) || session.me?.addresses?.[0])
const nearbyOnline = computed(() => nearbyRiders.value.filter((r) => r.onlineStatus !== 'OFFLINE').length)
const nearbyOffline = computed(() => nearbyRiders.value.length - nearbyOnline.value)
const nearbyEta = computed(() => {
  if (!nearbyRiders.value.length) return '附近暂无骑手'
  return nearbyOnline.value + ' 人在线' + (nearbyOffline.value ? ' · ' + nearbyOffline.value + ' 人休息' : '')
})
const nearbyUser = computed(() => (address.value?.lat != null && address.value?.lon != null)
  ? { lat: address.value.lat, lon: address.value.lon }
  : null)

async function loadNearby() {
  const loc = address.value
  if (loc?.lat == null || loc?.lon == null) {
    nearbyRiders.value = []
    return
  }
  try {
    const res = await api(`/api/riders/nearby?lat=${loc.lat}&lon=${loc.lon}&radiusMeters=5000&onlineStatus=ALL&acceptStatus=ALL`)
    nearbyRiders.value = Array.isArray(res.data) ? res.data : []
  } catch {
    nearbyRiders.value = []
  }
}

async function loadPage(reset) {
  if (loadingMore.value) return
  if (!reset && (!hasNext.value || reachedEnd.value)) return
  loadingMore.value = true
  try {
    const nextPage = reset ? 1 : page.value
    const query = q.value.trim()
    const url = query
      ? `/api/merchants?q=${encodeURIComponent(query)}&page=${nextPage}&size=12`
      : `/api/merchants/recommend?page=${nextPage}&size=12`
    const m = await api(url)
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

function resetList() {
  reachedEnd.value = false
  hasNext.value = true
}

function goRecommend() {
  draft.value = ''
  if (route.query.q) {
    router.replace({ path: '/home' })
    return
  }
  q.value = ''
  resetList()
  loadPage(true)
}

function submitSearch() {
  const keyword = draft.value.trim()
  if (!keyword) {
    goRecommend()
    return
  }
  if (String(route.query.q || '') === keyword) {
    q.value = keyword
    resetList()
    loadPage(true)
    return
  }
  router.replace({ path: '/home', query: { q: keyword } })
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
    resetList()
    await Promise.all([loadPage(true), loadNearby()])
    toast(searching.value ? '定位已更新，搜索结果已刷新' : '定位已更新，推荐已按新地址刷新')
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
  const keyword = String(route.query.q || '').trim()
  draft.value = keyword
  q.value = keyword
  await Promise.all([loadFeed(), loadPage(true), loadNearby(), maybeLoginGift()])
  observer = new IntersectionObserver((entries) => {
    if (entries.some((en) => en.isIntersecting)) loadPage(false)
  }, { rootMargin: '120px' })
  if (sentinel.value) observer.observe(sentinel.value)
  feedTimer = setInterval(loadFeed, 60000)
})
watch(() => String(route.query.q || ''), (next) => {
  const keyword = next.trim()
  if (keyword === q.value && draft.value === keyword) return
  draft.value = keyword
  q.value = keyword
  resetList()
  loadPage(true)
})
onUnmounted(() => {
  observer?.disconnect()
  clearInterval(feedTimer)
})
</script>

<template>
  <div class="phone page">
    <header class="frost home-head pad">
      <button class="loc-btn" type="button" @click="locOpen = true">
        <span class="loc-pin" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7zm0 9.5A2.5 2.5 0 1 1 12 6a2.5 2.5 0 0 1 0 5.5z"/></svg>
        </span>
        <span>{{ address?.detail || '请选择收货定位' }}</span>
        <span class="muted">切换</span>
      </button>
      <form class="search" @submit.prevent="submitSearch">
        <span aria-hidden="true">⌕</span>
        <input v-model="draft" type="search" enterkeyhint="search" placeholder="搜索商家、美食、商品名" />
        <button v-if="draft || searching" class="search-clear" type="button" @click="goRecommend">取消</button>
        <button class="search-go" type="submit">搜索</button>
      </form>
    </header>
    <div class="phone-body">
      <div class="pad">
        <template v-if="!searching">
          <div class="jingang">
            <div class="grid-icons">
              <button v-for="c in CATEGORIES" :key="c.key" class="icon-cell" type="button" @click="router.push('/category/' + c.key)">
                <img class="icon-ball" :src="c.image" :alt="c.name" :data-category="c.key" :data-seed="'cat-' + c.key" @error="onImgError" />
                {{ c.name }}
              </button>
            </div>
          </div>
          <button v-if="seckill" class="seckill-bar" type="button" @click="router.push('/activities/' + seckill.id)">
            <img
              :src="imgSrc(seckillCover, 'fresh', 'seckill-' + (seckill.currentSku?.skuId || seckill.id), 'dish')"
              data-category="fresh"
              :data-seed="'seckill-' + (seckill.currentSku?.skuId || seckill.id)"
              alt=""
              @error="onImgError"
            />
            <div class="seckill-copy">
              <b>{{ seckill.currentSku?.name || seckill.name }}</b>
              <span class="muted">限时秒杀 · 抢完不换品</span>
            </div>
            <span class="seckill-go">去抢</span>
          </button>
          <div v-if="homeFeed.items?.length" class="home-coupons" @click="openHomeCoupons">
            <div class="row" style="justify-content:space-between;margin-bottom:8px">
              <b class="section-title" style="margin:0">限时神券</b>
              <span class="muted">{{ homeFeed.claimed ? '已领取 · 去使用' : '横滑查看 · 点开领取' }}</span>
            </div>
            <div class="coupon-spread">
              <CouponFace v-for="c in homeFeed.items" :key="c.code" :coupon="c" compact mode="cover" />
            </div>
          </div>
          <div v-if="nearbyUser" class="nearby-riders">
            <div class="row" style="justify-content:space-between;margin-bottom:8px">
              <b class="section-title" style="margin:0">附近骑手</b>
              <span class="muted">{{ nearbyEta }}</span>
            </div>
            <SsdMap compact :riders="nearbyRiders" :user="nearbyUser" :eta="nearbyEta" />
            <p class="muted nearby-legend">橙色在线可接，灰色为休息中</p>
          </div>
        </template>
        <div class="home-feed-tabs">
          <button type="button" :class="{ on: !searching }" @click="goRecommend">推荐</button>
          <button type="button" :class="{ on: searching }" @click="draft.trim() ? submitSearch() : null">搜索结果</button>
        </div>
        <h3 class="section-title">{{ searching ? ('搜索结果 · ' + q) : '为你推荐' }}</h3>
        <div v-if="loading">
          <div class="skel" style="height:168px;margin-bottom:12px"></div>
          <div class="skel" style="height:168px"></div>
        </div>
        <article v-for="m in merchants" :key="m.id" class="store" @click="router.push('/shops/' + m.id + (m.hitSkus?.[0]?.id ? ('?skuId=' + m.hitSkus[0].id) : ''))">
          <div class="store-cover" :class="{ 'is-off': !shopIsOpen(m) }">
            <img
              class="store-cover-img"
              :src="imgSrc(m.coverUrl, m.category || 'food', 'shop-' + m.id, 'shop')"
              :data-seed="'shop-' + m.id"
              :data-category="m.category || 'food'"
              alt=""
              @error="onImgError"
            />
            <span v-if="!shopIsOpen(m)" class="cover-tag">休息中</span>
          </div>
          <div class="store-body">
            <div class="store-name">{{ m.shopName }}</div>
            <div class="store-meta">
              <span class="score">{{ (m.rating || 4.8) }} 分</span>
              <span>月售 {{ m.completedCount || 0 }}</span>
              <span v-if="m.distanceKm != null">{{ m.distanceKm }} km</span>
              <span>{{ m.promo || '配送费按距离结算' }}</span>
            </div>
            <div v-if="m.inRange === false" class="muted" style="margin-top:6px">超配送范围不可下单</div>
            <div v-if="m.hitSkus?.length" class="hit-skus">
              <span v-for="s in m.hitSkus" :key="s.id" class="hit-sku">{{ s.name }}</span>
            </div>
          </div>
        </article>
        <div ref="sentinel" style="height:8px"></div>
        <p v-if="loadingMore" class="muted" style="text-align:center">加载中…</p>
        <p v-if="reachedEnd && merchants.length" class="muted" style="text-align:center;padding:12px 0 24px">已经到底了</p>
        <EmptyState
          v-if="!loading && !merchants.length"
          :title="searching ? '没有匹配的店铺' : '附近暂无推荐'"
          :hint="searching
            ? '试试店名、品类或地址关键词'
            : ('附近 ' + (address?.detail || '当前定位') + ' 5 公里内暂无店铺，试试切换定位')"
        />
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
