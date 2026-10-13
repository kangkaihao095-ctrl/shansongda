<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { addSku, cart, goodsCents, itemCount, setQty, setShop } from '../cart'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { categoryName } from '../catalog'
import { toast } from '../session'
import MemberBadge from '../components/MemberBadge.vue'
import StarPicker from '../components/StarPicker.vue'
import PageHeader from '../components/PageHeader.vue'
import SegTabs from '../components/SegTabs.vue'
import BottomSheet from '../components/BottomSheet.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'

const route = useRoute()
const router = useRouter()
const shop = ref({ items: [] })
const loaded = ref(false)
const loadError = ref('')
const tab = ref('menu')
const reviews = ref([])
const reviewPage = ref(1)
const reviewHasNext = ref(false)
const reviewsLoading = ref(false)
const drawer = ref(null)
const activeGroup = ref('')
const bodyEl = ref(null)
const scrolled = ref(false)
const bump = ref(false)
let spy

const closed = computed(() => shop.value.open === false || shop.value.onlineStatus === 'OFFLINE')
const outOfRange = computed(() => shop.value.inRange === false)
const cat = computed(() => shop.value.category || 'food')
const groups = computed(() => {
  const map = new Map()
  for (const sku of shop.value.items || []) {
    const key = sku.groupName || '推荐'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(sku)
  }
  return [...map.entries()]
})
const currentGroup = computed(() => activeGroup.value || groups.value[0]?.[0])
const tabOptions = computed(() => [
  { key: 'menu', label: '点菜' },
  { key: 'reviews', label: `评价${shop.value.ratingCount ? ' ' + shop.value.ratingCount : ''}` }
])

async function load() {
  loadError.value = ''
  try {
    shop.value = (await api('/api/merchants/' + route.params.id)).data
    setShop(shop.value)
    loaded.value = true
    await nextTick()
    setupSpy()
    if (route.query.skuId) {
      document.getElementById('sku-' + route.query.skuId)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
    if (route.query.reviewId) {
      switchTab('reviews')
      await focusReview(route.query.reviewId)
    }
  } catch (e) {
    loadError.value = e.message || '店铺加载失败'
    loaded.value = true
    toast(e.message, 'err')
  }
}

onMounted(load)
onUnmounted(() => spy?.disconnect())

function setupSpy() {
  spy?.disconnect()
  if (typeof IntersectionObserver === 'undefined' || !bodyEl.value) return
  spy = new IntersectionObserver((entries) => {
    const hit = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0]
    if (hit) activeGroup.value = hit.target.dataset.group
  }, { root: bodyEl.value, rootMargin: '-120px 0px -65% 0px' })
  bodyEl.value.querySelectorAll('[data-group]').forEach((el) => spy.observe(el))
}

function onScroll(e) {
  scrolled.value = e.target.scrollTop > 140
}

async function loadReviews(reset) {
  reviewsLoading.value = true
  try {
    const p = reset ? 1 : reviewPage.value
    const res = await api(`/api/merchants/${route.params.id}/reviews?page=${p}&size=10`)
    const items = res.data?.items || []
    reviews.value = reset ? items : reviews.value.concat(items)
    reviewHasNext.value = !!res.data?.hasNext
    reviewPage.value = p + 1
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    reviewsLoading.value = false
  }
}

async function focusReview(reviewId) {
  const target = Number(reviewId)
  if (!target) return
  for (let i = 0; i < 8; i++) {
    if (reviews.value.some((r) => r.id === target)) break
    if (!reviewHasNext.value && reviews.value.length) break
    await loadReviews(!reviews.value.length && i === 0 ? true : false)
    if (!reviewHasNext.value) break
  }
  await nextTick()
  document.getElementById('review-' + target)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function switchTab(next) {
  tab.value = next
  if (next === 'reviews' && !reviews.value.length) loadReviews(true)
  if (next === 'menu') {
    await nextTick()
    setupSpy()
  }
}

function add(sku) {
  if (closed.value) return toast('商家休息，暂不可下单', 'err')
  if (outOfRange.value) return toast('超配送范围不可下单', 'err')
  if (sku.status === 'OFFLINE') return toast('商品已下架', 'err')
  setShop(shop.value)
  addSku(sku)
  bump.value = false
  requestAnimationFrame(() => { bump.value = true })
  toast('已加入购物车')
}

function qtyOf(id) {
  return cart.items.find((it) => it.skuId === id)?.qty || 0
}

function openSku(sku) {
  drawer.value = sku
}

async function toggleLike(row) {
  try {
    const res = await api('/api/reviews/' + row.id + '/like', { method: 'POST' })
    row.likeCount = res.data.likeCount
    row.likedByMe = res.data.likedByMe
  } catch (e) {
    toast(e.message, 'err')
  }
}

function scrollGroup(name) {
  activeGroup.value = name
  document.getElementById('g-' + name)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function timeText(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

function disabledLabel(sku) {
  if (sku.status === 'OFFLINE') return '已下架'
  if (closed.value) return '休息中'
  if (outOfRange.value) return '超范围'
  return ''
}
</script>

<template>
  <div class="phone page shop">
    <PageHeader overlay :solid="scrolled" back="/home" :title="scrolled ? shop.shopName : ''">
      <template #right>
        <span class="tag" :class="closed ? 'tag-neutral' : 'tag-success'"><i class="dot"></i>{{ closed ? '休息中' : '营业中' }}</span>
      </template>
    </PageHeader>

    <div ref="bodyEl" class="phone-body" :class="itemCount() && !closed && !outOfRange ? 'has-bar' : 'no-tab'" @scroll.passive="onScroll">
      <EmptyState v-if="loadError" kind="error" title="店铺加载失败" :hint="loadError" class="shop-error">
        <button class="btn btn-sm" type="button" @click="load"><Icon name="refresh" :size="16" />重试</button>
      </EmptyState>

      <template v-else>
        <div class="shop-hero">
          <img
            v-if="loaded"
            class="shop-cover"
            :class="{ 'is-off': closed }"
            :src="imgSrc(shop.coverUrl, cat, 'shop-' + shop.id, 'shop')"
            alt=""
            :data-seed="'shop-' + shop.id"
            :data-category="cat"
            @error="onImgError"
          />
          <div v-else class="shop-cover skel" style="border-radius:0"></div>
        </div>

        <section class="shop-info">
          <template v-if="loaded">
            <div class="si-head">
              <h1 class="t-h2">{{ shop.shopName || '店铺' }}</h1>
              <span class="si-score"><Icon name="star" :size="15" :stroke="0" class="si-star" />{{ Number(shop.rating || 4.8).toFixed(1) }}</span>
            </div>
            <div class="si-meta">
              <span>{{ categoryName(cat) }}</span><i></i>
              <span>{{ shop.ratingCount || 0 }} 条评价</span>
              <template v-if="shop.distanceKm != null"><i></i><span>{{ shop.distanceKm }} km</span></template>
              <template v-if="shop.completedCount"><i></i><span>月售 {{ shop.completedCount }}</span></template>
            </div>
            <div class="si-tags">
              <span v-if="shop.promo" class="tag">{{ shop.promo }}</span>
              <span class="tag tag-neutral">配送费按下单结算</span>
            </div>
            <p v-if="shop.address" class="si-line"><Icon name="pin" :size="14" />{{ shop.address }}</p>
            <p v-if="shop.intro" class="si-line"><Icon name="info" :size="14" />{{ shop.intro }}</p>
          </template>
          <template v-else>
            <div class="skel skel-line" style="width:60%;height:20px"></div>
            <div class="skel skel-line mt-3" style="width:80%"></div>
            <div class="skel skel-line mt-2" style="width:40%"></div>
          </template>
        </section>

        <div class="pad">
          <div v-if="closed" class="notice danger"><Icon name="moon" :size="16" />商家休息中，暂不可加购和下单</div>
          <div v-else-if="outOfRange" class="notice danger"><Icon name="info" :size="16" />超配送范围不可下单（{{ shop.distanceKm }} km / {{ shop.maxKm || 5 }} km）</div>
        </div>

        <div class="shop-tabs">
          <SegTabs :model-value="tab" :options="tabOptions" label="店铺内容" @change="switchTab" />
        </div>

        <div v-if="tab === 'menu'" class="menu-board">
          <nav class="menu-cats" aria-label="商品分类">
            <button
              v-for="[name, skus] in groups"
              :key="name"
              type="button"
              :class="{ on: currentGroup === name }"
              :aria-current="currentGroup === name ? 'true' : undefined"
              @click="scrollGroup(name)"
            >
              <span>{{ name }}</span>
              <small>{{ skus.length }}</small>
            </button>
          </nav>
          <div class="menu-pane">
            <div v-if="!loaded">
              <div v-for="n in 4" :key="n" class="sku-skel">
                <div class="skel" style="width:84px;height:84px"></div>
                <div class="grow"><div class="skel skel-line" style="width:70%"></div><div class="skel skel-line mt-2" style="width:45%"></div></div>
              </div>
            </div>
            <section v-for="[name, skus] in groups" :id="'g-' + name" :key="name" class="menu-group" :data-group="name">
              <h3 class="menu-group-title">{{ name }}</h3>
              <article
                v-for="sku in skus"
                :id="'sku-' + sku.id"
                :key="sku.id"
                class="sku"
                :class="{ highlight: String(route.query.skuId) === String(sku.id), off: sku.status === 'OFFLINE' }"
                @click="openSku(sku)"
              >
                <div class="sku-img">
                  <img :src="imgSrc(sku.imageUrl, cat, 'sku-' + sku.id, 'dish')" :alt="sku.name" :data-seed="'sku-' + sku.id" :data-category="cat" loading="lazy" @error="onImgError" />
                </div>
                <div class="sku-body">
                  <b class="sku-name">{{ sku.name }}</b>
                  <p class="sku-desc clamp-2">{{ sku.description || sku.spec }}</p>
                  <span class="sku-sales">月售 {{ sku.monthSales || 0 }}</span>
                  <div class="sku-foot">
                    <span class="sku-price">
                      <b class="num">{{ yuan(sku.priceCents) }}</b>
                      <s v-if="sku.originPriceCents > sku.priceCents" class="num">{{ yuan(sku.originPriceCents) }}</s>
                    </span>
                    <div class="qty" @click.stop>
                      <span v-if="disabledLabel(sku)" class="tag tag-neutral">{{ disabledLabel(sku) }}</span>
                      <template v-else>
                        <transition name="qty">
                          <button v-if="qtyOf(sku.id)" class="qty-minus" type="button" :aria-label="'减少 ' + sku.name" @click="setQty(sku.id, qtyOf(sku.id) - 1)">
                            <Icon name="minus" :size="14" :stroke="2.6" />
                          </button>
                        </transition>
                        <span v-if="qtyOf(sku.id)" class="qty-num">{{ qtyOf(sku.id) }}</span>
                        <button class="qty-plus" type="button" :aria-label="'加入 ' + sku.name" @click="add(sku)">
                          <Icon name="plus" :size="14" :stroke="2.6" />
                        </button>
                      </template>
                    </div>
                  </div>
                </div>
              </article>
            </section>
            <EmptyState v-if="loaded && !groups.length" bare title="店铺暂未上架商品" hint="稍后再来看看吧" />
          </div>
        </div>

        <div v-else class="pad reviews">
          <article
            v-for="r in reviews"
            :id="'review-' + r.id"
            :key="r.id"
            v-reveal
            class="card review"
            :class="{ highlight: String(route.query.reviewId) === String(r.id) }"
          >
            <div class="rv-head">
              <span class="avatar rv-ava">{{ (r.anonymousName || '匿').slice(0, 1) }}</span>
              <div class="grow">
                <div class="row">
                  <b>{{ r.anonymousName }}</b>
                  <MemberBadge :member="r.member" compact />
                </div>
                <div class="row mt-1">
                  <StarPicker :model-value="r.score || 0" size="sm" readonly />
                  <span class="muted">{{ timeText(r.createdAt) }}</span>
                </div>
              </div>
            </div>
            <p v-if="r.content" class="rv-content">{{ r.content }}</p>
            <div v-if="r.photoUrls?.length" class="rv-photos">
              <img v-for="url in r.photoUrls" :key="url" :src="url" alt="评价配图" loading="lazy" @error="onImgError" />
            </div>
            <div class="rv-foot">
              <span class="muted ellipsis">{{ r.skuNames || '本店商品' }}</span>
              <button class="like" :class="{ on: r.likedByMe }" type="button" :aria-pressed="!!r.likedByMe" @click="toggleLike(r)">
                <Icon name="thumb" :size="15" />{{ r.likeCount || 0 }}
              </button>
            </div>
          </article>
          <div v-if="reviewsLoading" class="list-status"><span class="spinner"></span>加载中…</div>
          <button v-if="reviewHasNext && !reviewsLoading" class="btn ghost btn-block" type="button" @click="loadReviews(false)">加载更多评价</button>
          <EmptyState v-if="!reviews.length && !reviewsLoading" title="还没有评价" hint="下单后来做第一个评价的人吧" />
        </div>
      </template>
    </div>

    <BottomSheet :open="!!drawer" @close="drawer = null">
      <template v-if="drawer">
        <div class="drawer-img">
          <img :src="imgSrc(drawer.imageUrl, cat, 'sku-' + drawer.id, 'dish')" :alt="drawer.name" :data-seed="'sku-' + drawer.id" :data-category="cat" @error="onImgError" />
        </div>
        <h3 class="t-h2 mt-4">{{ drawer.name }}</h3>
        <div class="row mt-2">
          <b class="num drawer-price">{{ yuan(drawer.priceCents) }}</b>
          <s v-if="drawer.originPriceCents > drawer.priceCents" class="num strike">{{ yuan(drawer.originPriceCents) }}</s>
          <span class="muted">月售 {{ drawer.monthSales || 0 }}<template v-if="drawer.spec"> · {{ drawer.spec }}</template></span>
        </div>
        <p v-if="drawer.description" class="drawer-desc">{{ drawer.description }}</p>
        <p v-if="drawer.detail" class="muted mt-2">{{ drawer.detail }}</p>
      </template>
      <template #footer>
        <button class="btn btn-lg btn-block" :disabled="!drawer || closed || outOfRange || drawer.status === 'OFFLINE'" @click="add(drawer); drawer = null">
          <Icon name="plus" :size="18" :stroke="2.4" />{{ outOfRange ? '超配送范围不可下单' : (closed ? '商家休息中' : (drawer?.status === 'OFFLINE' ? '商品已下架' : '加入购物车')) }}
        </button>
      </template>
    </BottomSheet>

    <transition name="pop">
      <div v-if="itemCount() && !closed && !outOfRange" class="cart-bar" role="button" tabindex="0" aria-label="去结算" @click="router.push('/checkout')" @keydown.enter="router.push('/checkout')">
        <span class="cart-thumb" :class="{ bump }" @animationend="bump = false">
          <img :src="imgSrc(cart.coverUrl || shop.coverUrl, cat, 'cart-cover', 'shop')" alt="" data-seed="cart-cover" :data-category="cat" @error="onImgError" />
          <span class="cart-count num">{{ itemCount() }}</span>
        </span>
        <span class="cart-sum">
          <b class="num">{{ yuan(goodsCents()) }}</b>
          <small>另需配送费 · 结算时自动用最优券</small>
        </span>
        <span class="btn">去结算</span>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.shop-hero { position: relative; height: 230px; overflow: hidden; background: var(--ink-150); }
.shop-cover { width: 100%; height: 100%; object-fit: cover; animation: hero-zoom 1.2s var(--ease-out) both; }
.shop-cover.is-off { filter: grayscale(0.5) brightness(0.85); }
.shop-hero::after { content: ""; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0, 0, 0, 0.35), transparent 40%, rgba(0, 0, 0, 0.15)); pointer-events: none; }
@keyframes hero-zoom { from { transform: scale(1.08); } to { transform: none; } }
.shop-error { margin: 80px var(--gutter) 0; }

.shop-info {
  position: relative; z-index: 2;
  margin: -44px var(--gutter) 0;
  padding: 18px 18px 16px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-3);
  animation: rise var(--dur-4) var(--ease-out) both;
}
.si-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.si-score { display: inline-flex; align-items: center; gap: 3px; padding: 4px 10px; border-radius: var(--r-pill); background: var(--warning-50); color: var(--flame-600); font-family: var(--font-num); font-weight: 800; font-size: var(--fs-md); flex-shrink: 0; }
.si-star { fill: #FFB300; color: #FFB300; }
.si-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 6px; font-size: var(--fs-sm); color: var(--text-3); }
.si-meta i { width: 3px; height: 3px; border-radius: 50%; background: var(--ink-300); }
.si-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.si-line { display: flex; align-items: flex-start; gap: 6px; margin-top: 10px; font-size: var(--fs-sm); color: var(--text-2); line-height: 1.5; }
.si-line .ico { margin-top: 2px; color: var(--text-3); }

.notice { display: flex; align-items: center; gap: 8px; margin-top: 12px; padding: 10px 14px; border-radius: var(--r-md); font-size: var(--fs-sm); font-weight: 650; }
.notice.danger { background: var(--danger-50); color: var(--danger-600); }

.shop-tabs {
  position: sticky; top: 0; z-index: 5;
  padding: 12px var(--gutter) 10px;
  background: color-mix(in srgb, var(--canvas) 90%, transparent);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

.menu-board { display: flex; align-items: flex-start; }
.menu-cats {
  position: sticky; top: 60px;
  width: 86px; flex-shrink: 0;
  padding: 4px 0 24px;
  display: flex; flex-direction: column;
}
.menu-cats button {
  position: relative;
  display: flex; flex-direction: column; align-items: flex-start; gap: 1px;
  padding: 12px 10px 12px 16px;
  text-align: left;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-2);
  transition: color var(--dur-2) ease, background var(--dur-2) ease;
}
.menu-cats button small { font-size: var(--fs-2xs); color: var(--text-3); font-family: var(--font-num); }
.menu-cats button::before {
  content: ""; position: absolute; left: 6px; top: 50%;
  width: 3px; height: 0; border-radius: 3px;
  background: var(--accent-grad);
  transform: translateY(-50%);
  transition: height var(--dur-3) var(--ease-out);
}
.menu-cats button.on { color: var(--text); font-weight: 800; background: var(--surface); border-radius: 0 14px 14px 0; box-shadow: var(--sh-1); }
.menu-cats button.on::before { height: 18px; }
.menu-pane { flex: 1; min-width: 0; padding: 0 var(--gutter) 0 10px; }
.menu-group { scroll-margin-top: 70px; }
.menu-group-title { font-size: var(--fs-sm); font-weight: 750; color: var(--text-2); margin: 10px 2px 8px; letter-spacing: 0.02em; }
.sku {
  display: flex; gap: 12px;
  padding: 10px;
  margin-bottom: 10px;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
  cursor: pointer;
  transition: box-shadow var(--dur-3) ease, transform var(--dur-2) ease;
}
@media (hover: hover) { .sku:hover { box-shadow: var(--sh-2); } .sku:hover .sku-img img { transform: scale(1.06); } }
.sku.highlight { box-shadow: 0 0 0 2px var(--accent), var(--sh-2); }
.sku.off { opacity: 0.6; }
.sku-img { width: 88px; height: 88px; border-radius: 14px; overflow: hidden; flex-shrink: 0; background: var(--ink-150); }
.sku-img img { width: 100%; height: 100%; object-fit: cover; transition: transform var(--dur-4) var(--ease-out); }
.sku-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.sku-name { font-size: var(--fs-md); font-weight: 700; line-height: 1.3; }
.sku-desc { font-size: var(--fs-xs); color: var(--text-3); margin-top: 3px; line-height: 1.45; }
.sku-sales { font-size: var(--fs-2xs); color: var(--text-3); margin-top: 3px; }
.sku-foot { display: flex; align-items: center; justify-content: space-between; gap: 6px; margin-top: auto; padding-top: 6px; }
.sku-price { display: inline-flex; align-items: baseline; gap: 4px; }
.sku-price b { font-size: var(--fs-lg); font-weight: 800; color: var(--flame-600); }
.sku-price s { font-size: var(--fs-2xs); color: var(--text-3); }
.sku-skel { display: flex; gap: 12px; padding: 10px; margin-bottom: 10px; border-radius: var(--r-lg); background: var(--surface); }

.reviews { padding-top: 4px; }
.review + .review { margin-top: 10px; }
.rv-head { display: flex; gap: 10px; align-items: center; }
.rv-ava { width: 38px; height: 38px; font-size: var(--fs-md); box-shadow: none; }
.rv-content { margin-top: 10px; font-size: var(--fs-base); line-height: var(--lh-normal); }
.rv-photos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 10px; }
.rv-photos img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 10px; }
.rv-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 10px; }
.like {
  display: inline-flex; align-items: center; gap: 4px; flex-shrink: 0;
  height: 30px; padding: 0 12px;
  border-radius: var(--r-pill);
  font-size: var(--fs-sm); font-weight: 700; color: var(--text-2);
  background: var(--surface-2); box-shadow: inset 0 0 0 1px var(--line);
  font-family: var(--font-num);
  transition: background var(--dur-2) ease, color var(--dur-2) ease, transform var(--dur-2) var(--ease-spring);
}
.like:active { transform: scale(0.9); }
.like.on { background: var(--accent-soft); color: var(--accent-strong); box-shadow: none; }
.like.on .ico { animation: badge-pop var(--dur-3) var(--ease-spring); }
.list-status { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 16px 0; color: var(--text-3); font-size: var(--fs-sm); }

.drawer-img { margin: 0 -20px; height: 240px; overflow: hidden; }
.drawer-img img { width: 100%; height: 100%; object-fit: cover; }
.drawer-price { font-size: var(--fs-2xl); font-weight: 800; color: var(--flame-600); }
.drawer-desc { margin-top: 12px; font-size: var(--fs-base); line-height: var(--lh-normal); color: var(--text-2); }

.cart-thumb.bump { animation: cart-bump 0.45s var(--ease-spring); }
@keyframes cart-bump { 0% { transform: scale(1); } 40% { transform: scale(1.18) rotate(-6deg); } 100% { transform: scale(1); } }
</style>
