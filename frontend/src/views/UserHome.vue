<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import CouponFace from '../components/CouponFace.vue'
import CouponSheet from '../components/CouponSheet.vue'
import LocationSheet from '../components/LocationSheet.vue'
import EmptyState from '../components/EmptyState.vue'
import SsdMap from '../components/SsdMap.vue'
import Icon from '../components/Icon.vue'
import SegTabs from '../components/SegTabs.vue'
import StoreCard from '../components/StoreCard.vue'
import { CATEGORIES } from '../catalog'
import { yuan } from '../brand'
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

const route = useRoute()
const router = useRouter()
const merchants = ref([])
const activities = ref([])
const homeFeed = ref({ items: [], claimed: false })
const loading = ref(true)
const loadError = ref('')
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
const now = ref(Date.now())
let observer
let feedTimer
let clock

const seckill = computed(() => activities.value.find((a) => a.type === 'SECKILL'))
const seckillSku = computed(() => seckill.value?.currentSku || seckill.value?.skus?.[0] || null)
const seckillCover = computed(() => seckillSku.value?.imageUrl)
const seckillLeft = computed(() => {
  const end = seckill.value?.endAt ? new Date(seckill.value.endAt).getTime() : 0
  const ms = Math.max(0, end - now.value)
  if (!end) return null
  const pad = (x) => String(x).padStart(2, '0')
  const days = Math.floor(ms / 86400000)
  return {
    days,
    parts: [Math.floor((ms % 86400000) / 3600000), Math.floor((ms % 3600000) / 60000), Math.floor((ms % 60000) / 1000)].map(pad)
  }
})
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
const feedTab = computed({
  get: () => (searching.value ? 'search' : 'rec'),
  set: (v) => { if (v === 'rec') goRecommend(); else if (draft.value.trim()) submitSearch() }
})
const greeting = computed(() => {
  const h = new Date(now.value).getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

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
  if (reset) loadError.value = ''
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
    if (reset) loadError.value = e.message || '加载失败'
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

function openShop(m) {
  router.push('/shops/' + m.id + (m.hitSkus?.[0]?.id ? ('?skuId=' + m.hitSkus[0].id) : ''))
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
  clock = setInterval(() => { now.value = Date.now() }, 1000)
  api('/api/activities')
    .then((a) => { activities.value = a.data || [] })
    .catch(() => { activities.value = [] })
  const keyword = String(route.query.q || '').trim()
  draft.value = keyword
  q.value = keyword
  await Promise.all([loadFeed(), loadPage(true), loadNearby(), maybeLoginGift()])
  observer = new IntersectionObserver((entries) => {
    if (entries.some((en) => en.isIntersecting)) loadPage(false)
  }, { rootMargin: '160px' })
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
  clearInterval(clock)
})
</script>

<template>
  <div class="phone page home">
    <header class="home-head">
      <div class="home-top">
        <button class="home-loc" type="button" aria-label="切换收货定位" @click="locOpen = true">
          <span class="home-loc-ico"><Icon name="pin" :size="16" :stroke="2.2" /></span>
          <span class="home-loc-text">
            <small>{{ greeting }}，送至</small>
            <b class="ellipsis">{{ address?.detail || '请选择收货定位' }}</b>
          </span>
          <Icon name="chevron-down" :size="16" :stroke="2.4" />
        </button>
        <button class="icon-btn home-coupon-btn" type="button" aria-label="我的优惠券" @click="router.push('/coupons')">
          <Icon name="ticket" :size="20" />
        </button>
      </div>
      <form class="search home-search" role="search" @submit.prevent="submitSearch">
        <Icon name="search" :size="18" />
        <input v-model="draft" type="search" enterkeyhint="search" placeholder="搜索商家、美食、商品名" aria-label="搜索商家、美食、商品名" />
        <button v-if="draft || searching" class="icon-btn sm plain" type="button" aria-label="取消搜索" @click="goRecommend">
          <Icon name="close" :size="14" />
        </button>
        <button class="btn btn-sm home-search-go" type="submit">搜索</button>
      </form>
      <div v-if="nearbyUser && !searching" class="home-live">
        <span class="pulse-dot" :class="{ off: !nearbyOnline }"></span>
        <span>{{ nearbyOnline ? `附近 ${nearbyOnline} 位骑手在线` : '附近骑手休息中' }} · 5 km 内即时配送</span>
      </div>
    </header>

    <div class="phone-body">
      <template v-if="!searching">
        <nav class="jingang pad" aria-label="品类">
          <button
            v-for="(c, i) in CATEGORIES"
            :key="c.key"
            v-reveal="i * 30"
            class="jg-cell"
            type="button"
            @click="router.push('/category/' + c.key)"
          >
            <span class="jg-img"><img :src="c.image" :alt="''" :data-category="c.key" :data-seed="'cat-' + c.key" loading="lazy" @error="onImgError" /></span>
            <span class="jg-name">{{ c.name }}</span>
          </button>
        </nav>

        <div class="pad">
          <button v-if="seckill" v-reveal class="seckill" type="button" @click="router.push('/activities/' + seckill.id)">
            <div class="sk-copy">
              <div class="sk-head">
                <span class="sk-badge"><Icon name="flame" :size="14" :stroke="2.2" />限时秒杀</span>
                <span v-if="seckillLeft" class="sk-clock" aria-label="距结束">
                  <em v-if="seckillLeft.days" class="num">{{ seckillLeft.days }}天</em>
                  <i class="num">{{ seckillLeft.parts[0] }}</i>:<i class="num">{{ seckillLeft.parts[1] }}</i>:<i class="num">{{ seckillLeft.parts[2] }}</i>
                </span>
              </div>
              <b class="sk-name ellipsis">{{ seckillSku?.name || seckill.name }}</b>
              <div class="sk-price">
                <span v-if="seckillSku?.priceCents" class="num">{{ yuan(seckillSku.priceCents) }}</span>
                <s v-if="seckillSku?.originPriceCents" class="num">{{ yuan(seckillSku.originPriceCents) }}</s>
                <small>手慢无，卖完即止</small>
              </div>
              <span class="sk-go">立即抢 <Icon name="chevron" :size="14" :stroke="2.6" /></span>
            </div>
            <div class="sk-img">
              <img
                :src="imgSrc(seckillCover, 'fresh', 'seckill-' + (seckillSku?.skuId || seckill.id), 'dish')"
                data-category="fresh"
                :data-seed="'seckill-' + (seckillSku?.skuId || seckill.id)"
                alt=""
                @error="onImgError"
              />
            </div>
          </button>

          <section v-if="homeFeed.items?.length" v-reveal class="section home-coupons">
            <div class="section-head">
              <h3 class="section-title"><Icon name="ticket" :size="18" class="text-accent" />限时神券</h3>
              <button class="section-more" type="button" @click="openHomeCoupons">
                {{ homeFeed.claimed ? '已领取 · 去使用' : '一键领取' }}<Icon name="chevron" :size="14" />
              </button>
            </div>
            <div class="coupon-spread" role="list" @click="openHomeCoupons">
              <CouponFace v-for="c in homeFeed.items" :key="c.code" :coupon="c" compact mode="cover" role="listitem" />
            </div>
          </section>

          <section v-if="nearbyUser" v-reveal class="section">
            <div class="section-head">
              <h3 class="section-title"><Icon name="bike" :size="18" class="text-accent" />附近骑手</h3>
              <span class="section-more">{{ nearbyEta }}</span>
            </div>
            <SsdMap compact :riders="nearbyRiders" :user="nearbyUser" :eta="nearbyEta" />
            <p class="muted mt-2 legend"><i class="lg-on"></i>在线可接单 <i class="lg-off"></i>休息中 · 附近有骑手在接单</p>
          </section>
        </div>
      </template>

      <div class="pad">
        <div class="feed-head" :class="{ searching }">
          <SegTabs v-model="feedTab" :options="[{ key: 'rec', label: '为你推荐' }, { key: 'search', label: '搜索结果' }]" label="列表切换" />
        </div>
        <p v-if="searching" class="muted mb-3">“{{ q }}” 的搜索结果</p>

        <div v-if="loading" aria-busy="true" aria-label="加载中">
          <div v-for="n in 3" :key="n" class="skel-card store-skel">
            <div class="skel" style="height:148px;border-radius:14px"></div>
            <div class="skel skel-line mt-3" style="width:56%"></div>
            <div class="skel skel-line mt-2" style="width:78%"></div>
          </div>
        </div>

        <EmptyState v-else-if="loadError && !merchants.length" kind="error" title="店铺加载失败" :hint="loadError">
          <button class="btn btn-sm" type="button" @click="loadPage(true)"><Icon name="refresh" :size="16" />重新加载</button>
        </EmptyState>

        <div v-else class="store-list">
          <StoreCard
            v-for="(m, i) in merchants"
            :key="m.id"
            v-reveal="(i % 12) * 40"
            :shop="m"
            @open="openShop(m)"
          />
        </div>

        <div ref="sentinel" class="sentinel" aria-hidden="true"></div>
        <div v-if="loadingMore && !loading" class="list-status"><span class="spinner"></span>加载中…</div>
        <div v-if="reachedEnd && merchants.length" class="list-status end"><i></i>已经到底了<i></i></div>
        <EmptyState
          v-if="!loading && !loadError && !merchants.length"
          :kind="searching ? 'search' : 'offline'"
          :title="searching ? '没有匹配的店铺' : '附近暂无推荐'"
          :hint="searching
            ? '试试店名、品类或商品关键词'
            : ('附近 ' + (address?.detail || '当前定位') + ' 5 公里内暂无店铺，试试切换定位')"
        >
          <button v-if="!searching" class="btn btn-sm ghost" type="button" @click="locOpen = true">切换定位</button>
        </EmptyState>
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

<style scoped>
.home-head {
  position: relative;
  z-index: var(--z-header);
  flex-shrink: 0;
  padding: calc(10px + env(safe-area-inset-top, 0px)) var(--gutter) 14px;
  background: var(--hero-grad);
  color: #1A1208;
  border-radius: 0 0 26px 26px;
  box-shadow: 0 12px 30px -18px rgba(255, 120, 0, 0.7);
}
.home-head::after {
  content: "";
  position: absolute; inset: 0;
  border-radius: inherit;
  background: radial-gradient(80% 120% at 100% 0%, rgba(255, 255, 255, 0.35), transparent 60%);
  pointer-events: none;
}
.home-top { position: relative; z-index: 1; display: flex; align-items: center; gap: 10px; }
.home-loc { flex: 1; min-width: 0; display: flex; align-items: center; gap: 8px; text-align: left; color: #1A1208; }
.home-loc-ico { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: rgba(255, 255, 255, 0.5); flex-shrink: 0; }
.home-loc-text { min-width: 0; display: flex; flex-direction: column; line-height: 1.2; }
.home-loc-text small { font-size: var(--fs-xs); font-weight: 600; opacity: 0.7; }
.home-loc-text b { font-size: var(--fs-md); font-weight: 800; max-width: 230px; }
.home-coupon-btn { background: rgba(255, 255, 255, 0.55); box-shadow: none; color: #1A1208; }
.home-search { position: relative; z-index: 1; margin-top: 12px; height: 46px; box-shadow: 0 10px 24px -12px rgba(160, 70, 0, 0.6); }
.home-search-go { --btn-h: 36px; flex-shrink: 0; }
.home-live {
  position: relative; z-index: 1;
  display: flex; align-items: center; gap: 8px;
  margin-top: 10px;
  font-size: var(--fs-xs); font-weight: 650;
  color: rgba(26, 18, 8, 0.78);
}

.jingang {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px 6px;
  padding-top: 18px; padding-bottom: 4px;
}
.jg-cell { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.jg-img {
  width: 56px; height: 56px;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 8px 18px -10px rgba(22, 20, 18, 0.45), 0 0 0 2px var(--surface);
  transition: transform var(--dur-3) var(--ease-spring), box-shadow var(--dur-3) ease;
}
.jg-img img { width: 100%; height: 100%; object-fit: cover; transition: transform var(--dur-4) var(--ease-out); }
@media (hover: hover) {
  .jg-cell:hover .jg-img { transform: translateY(-3px); box-shadow: 0 14px 24px -12px rgba(22, 20, 18, 0.5), 0 0 0 2px var(--surface); }
  .jg-cell:hover .jg-img img { transform: scale(1.08); }
}
.jg-cell:active .jg-img { transform: scale(0.92); }
.jg-name { font-size: var(--fs-sm); font-weight: 650; color: var(--text); }

.seckill {
  position: relative;
  display: flex; align-items: stretch;
  width: 100%;
  margin-top: var(--s-5);
  border-radius: var(--r-xl);
  overflow: hidden;
  text-align: left;
  color: #fff;
  background: radial-gradient(120% 140% at 0% 0%, #3A1410 0%, #1E0D0B 60%, #140A08 100%);
  box-shadow: 0 18px 36px -18px rgba(180, 40, 20, 0.7);
  transition: transform var(--dur-3) var(--ease-out), box-shadow var(--dur-3) ease;
}
@media (hover: hover) { .seckill:hover { transform: translateY(-2px); box-shadow: 0 24px 44px -18px rgba(180, 40, 20, 0.8); } }
.seckill:active { transform: scale(0.99); }
.sk-copy { flex: 1; min-width: 0; padding: 14px 8px 14px 14px; display: flex; flex-direction: column; gap: 6px; position: relative; z-index: 1; }
.sk-head { display: flex; align-items: center; gap: 6px 8px; flex-wrap: wrap; max-width: 100%; }
.sk-badge {
  display: inline-flex; align-items: center; gap: 4px;
  height: 22px; padding: 0 8px;
  border-radius: 6px;
  background: linear-gradient(135deg, #FF5A3C, #E5302A);
  font-size: var(--fs-xs); font-weight: 800;
  white-space: nowrap;
  flex: 0 0 auto;
  max-width: 100%;
}
.sk-clock { display: inline-flex; align-items: center; gap: 2px; max-width: 100%; flex-wrap: wrap; font-size: var(--fs-xs); font-weight: 700; color: rgba(255, 255, 255, 0.6); }
.sk-clock em { font-style: normal; margin-right: 2px; color: rgba(255, 255, 255, 0.75); white-space: nowrap; }
.sk-clock i { font-style: normal; min-width: 20px; height: 20px; padding: 0 3px; display: grid; place-items: center; border-radius: 5px; background: rgba(255, 255, 255, 0.12); color: #fff; }
.sk-name { font-size: var(--fs-lg); font-weight: 800; margin-top: 2px; max-width: 100%; }
.sk-price { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; max-width: 100%; }
.sk-price span { font-size: var(--fs-xl); font-weight: 800; color: #FFC31F; }
.sk-price s { font-size: var(--fs-xs); color: rgba(255, 255, 255, 0.45); }
.sk-price small { width: 100%; font-size: var(--fs-2xs); color: rgba(255, 255, 255, 0.5); overflow-wrap: anywhere; }
.sk-go {
  align-self: flex-start;
  display: inline-flex; align-items: center; gap: 2px;
  height: 30px; padding: 0 12px;
  margin-top: 2px;
  border-radius: var(--r-pill);
  background: var(--grad-brand);
  color: #1A1208;
  font-size: var(--fs-sm); font-weight: 800;
}
.sk-img { position: relative; width: min(132px, 34%); flex-shrink: 0; }
.sk-img img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; mask-image: linear-gradient(90deg, transparent, #000 40%); -webkit-mask-image: linear-gradient(90deg, transparent, #000 40%); transition: transform var(--dur-4) var(--ease-out); }
@media (hover: hover) { .seckill:hover .sk-img img { transform: scale(1.06); } }

.home-coupons .coupon-spread { cursor: pointer; }
.legend { display: flex; align-items: center; gap: 6px; font-size: var(--fs-xs); }
.legend i { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.lg-on { background: var(--flame-500); }
.lg-off { background: var(--ink-300); margin-left: 6px; }

.feed-head { position: sticky; top: 0; z-index: 4; margin: var(--s-6) calc(var(--gutter) * -1) var(--s-3); padding: 8px var(--gutter); background: color-mix(in srgb, var(--canvas) 88%, transparent); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); }
.feed-head.searching { margin-top: var(--s-4); }
.store-list { display: flex; flex-direction: column; gap: var(--s-3); }
.store-skel + .store-skel { margin-top: var(--s-3); }
.sentinel { height: 8px; }
.list-status { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 18px 0; font-size: var(--fs-sm); color: var(--text-3); }
.list-status .spinner { width: 14px; height: 14px; }
.list-status.end i { width: 36px; height: 1px; background: var(--line-strong); }
</style>
