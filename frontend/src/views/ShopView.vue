<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { addSku, cart, goodsCents, itemCount, setQty, setShop } from '../cart'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { goBack, toast } from '../session'
import MemberBadge from '../components/MemberBadge.vue'
import StarPicker from '../components/StarPicker.vue'

const route = useRoute()
const router = useRouter()
const shop = ref({ items: [] })
const tab = ref('menu')
const reviews = ref([])
const reviewPage = ref(1)
const reviewHasNext = ref(false)
const drawer = ref(null)
const groupEl = ref(null)
const activeGroup = ref('')

const closed = computed(() => shop.value.open === false || shop.value.onlineStatus === 'OFFLINE')
const outOfRange = computed(() => shop.value.inRange === false)
const groups = computed(() => {
  const map = new Map()
  for (const sku of shop.value.items || []) {
    const key = sku.groupName || '推荐'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(sku)
  }
  return [...map.entries()]
})

onMounted(async () => {
  try {
    shop.value = (await api('/api/merchants/' + route.params.id)).data
    setShop(shop.value)
    if (route.query.skuId) {
      await nextTick()
      document.getElementById('sku-' + route.query.skuId)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
    if (route.query.reviewId) {
      switchTab('reviews')
      await focusReview(route.query.reviewId)
    }
  } catch (e) {
    toast(e.message, 'err')
  }
})

async function loadReviews(reset) {
  try {
    const p = reset ? 1 : reviewPage.value
    const res = await api(`/api/merchants/${route.params.id}/reviews?page=${p}&size=10`)
    const items = res.data?.items || []
    reviews.value = reset ? items : reviews.value.concat(items)
    reviewHasNext.value = !!res.data?.hasNext
    reviewPage.value = p + 1
  } catch (e) {
    toast(e.message, 'err')
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

function switchTab(next) {
  tab.value = next
  if (next === 'reviews' && !reviews.value.length) loadReviews(true)
}

function add(sku) {
  if (closed.value) return toast('商家休息，暂不可下单', 'err')
  if (outOfRange.value) return toast('超配送范围不可下单', 'err')
  if (sku.status === 'OFFLINE') return toast('商品已下架', 'err')
  setShop(shop.value)
  addSku(sku)
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
  const el = document.getElementById('g-' + name)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function timeText(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}月${d.getDate()}日`
}
</script>

<template>
  <div class="phone page">
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router, '/home')">← 返回</button>
      <span class="pill" :class="{ off: closed }">{{ closed ? '休息中' : '营业中' }}</span>
    </header>
    <div class="phone-body no-tab">
      <div class="shop-hero">
        <img
          class="hero-cover"
          :class="{ 'is-off': closed }"
          :src="imgSrc(shop.coverUrl, shop.category || 'food', 'shop-' + shop.id, 'shop')"
          alt=""
          :data-seed="'shop-' + shop.id"
          :data-category="shop.category || 'food'"
          @error="onImgError"
        />
        <div class="shop-info">
          <h1>{{ shop.shopName || '店铺' }}</h1>
          <div class="shop-metrics">
            <span class="score">{{ shop.rating || 4.8 }} 分</span>
            <span>{{ shop.ratingCount || 0 }} 评</span>
            <span>{{ shop.promo || '配送费按下单结算' }}</span>
          </div>
          <div v-if="shop.address" class="muted" style="margin-top:6px">{{ shop.address }}</div>
          <div v-if="shop.intro" class="muted" style="margin-top:4px">{{ shop.intro }}</div>
        </div>
      </div>
      <div class="pad" style="padding-top:12px">
        <div v-if="closed" class="card" style="margin-bottom:12px;color:var(--danger)">商家休息中，暂不可加购和下单</div>
        <div v-else-if="outOfRange" class="card" style="margin-bottom:12px;color:var(--danger)">超配送范围不可下单（{{ shop.distanceKm }} km / {{ shop.maxKm || 5 }} km）</div>
        <div class="shop-tabs">
          <button type="button" :class="{ on: tab === 'menu' }" @click="switchTab('menu')">商品</button>
          <button type="button" :class="{ on: tab === 'reviews' }" @click="switchTab('reviews')">评价</button>
        </div>
      </div>
      <div v-if="tab === 'menu'" class="menu-board">
        <nav ref="groupEl" class="menu-cats">
          <button v-for="[name] in groups" :key="name" type="button" :class="{ on: (activeGroup || groups[0]?.[0]) === name }" @click="scrollGroup(name)">{{ name }}</button>
        </nav>
        <div class="menu-pane">
          <section v-for="[name, skus] in groups" :id="'g-' + name" :key="name" style="margin-bottom:8px">
            <h3 class="section-title" style="font-size:15px;margin:8px 4px">{{ name }}</h3>
            <article v-for="sku in skus" :id="'sku-' + sku.id" :key="sku.id" class="card sku-row" :class="{ highlight: String(route.query.skuId) === String(sku.id) }" @click="openSku(sku)">
              <img :src="imgSrc(sku.imageUrl, shop.category || 'food', 'sku-' + sku.id, 'dish')" :alt="sku.name" :data-seed="'sku-' + sku.id" :data-category="shop.category || 'food'" @error="onImgError" style="width:72px;height:72px;border-radius:14px;object-fit:cover" />
              <div style="flex:1">
                <b>{{ sku.name }}</b>
                <div class="muted">{{ sku.description || sku.spec }} · 月售 {{ sku.monthSales || 0 }}</div>
                <div class="row" style="justify-content:space-between;margin-top:6px">
                  <span class="num">{{ yuan(sku.priceCents) }} <span class="muted" style="text-decoration:line-through">{{ yuan(sku.originPriceCents) }}</span></span>
                  <div class="row" @click.stop>
                    <button v-if="qtyOf(sku.id) && !closed && sku.status !== 'OFFLINE'" class="btn ghost" @click="setQty(sku.id, qtyOf(sku.id) - 1)">-</button>
                    <span v-if="qtyOf(sku.id)">{{ qtyOf(sku.id) }}</span>
                    <button class="btn sku-plus" :disabled="closed || outOfRange || sku.status === 'OFFLINE'" @click="add(sku)">
                      {{ sku.status === 'OFFLINE' ? '已下架' : (closed ? '休息中' : (outOfRange ? '超范围' : '+')) }}
                    </button>
                  </div>
                </div>
              </div>
            </article>
          </section>
        </div>
      </div>
      <div v-else class="pad">
          <article
            v-for="r in reviews"
            :id="'review-' + r.id"
            :key="r.id"
            class="card"
            :class="{ highlight: String(route.query.reviewId) === String(r.id) }"
            style="margin-bottom:10px"
          >
            <div class="row" style="justify-content:space-between">
              <div class="row" style="gap:8px">
                <b>{{ r.anonymousName }}</b>
                <MemberBadge :member="r.member" compact />
              </div>
              <StarPicker :model-value="r.score || 0" size="sm" readonly />
            </div>
            <div class="muted" style="margin:6px 0">{{ timeText(r.createdAt) }} · {{ r.skuNames || '本店商品' }}</div>
            <div v-if="r.content">{{ r.content }}</div>
            <div v-if="r.photoUrls?.length" class="review-photos" style="margin-top:8px">
              <img v-for="url in r.photoUrls" :key="url" :src="url" alt="" class="review-thumb" @error="onImgError" />
            </div>
            <button class="like-btn" :class="{ on: r.likedByMe }" type="button" @click="toggleLike(r)">
              {{ r.likedByMe ? '已赞' : '点赞' }} {{ r.likeCount || 0 }}
            </button>
          </article>
          <button v-if="reviewHasNext" class="btn ghost" style="width:100%" @click="loadReviews(false)">加载更多评价</button>
          <p v-if="!reviews.length" class="muted">暂无评价</p>
      </div>
    </div>
    <div v-if="drawer" class="sku-mask" @click="drawer = null">
      <div class="sku-drawer" @click.stop>
        <img :src="imgSrc(drawer.imageUrl, shop.category || 'food', 'sku-' + drawer.id, 'dish')" :alt="drawer.name" :data-seed="'sku-' + drawer.id" :data-category="shop.category || 'food'" @error="onImgError" />
        <div class="pad">
          <div class="row" style="justify-content:space-between">
            <b style="font-size:18px">{{ drawer.name }}</b>
            <button class="back-btn" type="button" @click="drawer = null">关闭</button>
          </div>
          <div class="num" style="margin:8px 0">{{ yuan(drawer.priceCents) }}</div>
          <div class="muted">月售 {{ drawer.monthSales || 0 }} · {{ drawer.spec }}</div>
          <p>{{ drawer.description }}</p>
          <p class="muted">{{ drawer.detail }}</p>
          <button class="btn" style="width:100%" :disabled="closed || outOfRange || drawer.status === 'OFFLINE'" @click="add(drawer); drawer = null">
            {{ outOfRange ? '超配送范围不可下单' : '加入购物车' }}
          </button>
        </div>
      </div>
    </div>
    <div v-if="itemCount() && !closed && !outOfRange" class="cart-bar" @click="router.push('/checkout')">
      <img :src="imgSrc(cart.coverUrl || shop.coverUrl, shop.category || 'food', 'cart-cover', 'shop')" alt="" data-seed="cart-cover" :data-category="shop.category || 'food'" @error="onImgError" style="width:36px;height:36px;border-radius:10px;object-fit:cover" />
      <div style="flex:1">已选 {{ itemCount() }} 件 · {{ yuan(goodsCents()) }}</div>
      <b>去结算</b>
    </div>
  </div>
</template>
