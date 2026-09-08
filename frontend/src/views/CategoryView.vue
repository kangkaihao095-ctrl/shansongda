<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CATEGORIES, categoryName } from '../catalog'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { goBack, toast } from '../session'

const route = useRoute()
const router = useRouter()
const merchants = ref([])
const page = ref(1)
const hasNext = ref(false)
const loading = ref(false)
const cat = computed(() => CATEGORIES.find((c) => c.key === route.params.key))

async function load(reset) {
  if (loading.value) return
  loading.value = true
  try {
    const nextPage = reset ? 1 : page.value
    const res = await api(`/api/merchants?category=${route.params.key}&page=${nextPage}&size=15`)
    const items = res.data?.items || []
    merchants.value = reset ? items : merchants.value.concat(items)
    hasNext.value = !!res.data?.hasNext
    page.value = nextPage + 1
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

onMounted(() => load(true))
watch(() => route.params.key, () => {
  merchants.value = []
  page.value = 1
  load(true)
})
</script>

<template>
  <div class="phone page">
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router, '/home')">← 返回</button>
      <b>{{ cat?.name || categoryName(route.params.key) }}</b>
    </header>
    <div class="phone-body no-tab pad">
      <img v-if="cat" :src="cat.image" alt="" :data-seed="'cat-banner-' + cat.key" :data-category="cat.key" @error="onImgError" style="width:100%;height:140px;object-fit:cover;border-radius:18px;margin-bottom:14px" />
      <article v-for="m in merchants" :key="m.id" class="store" @click="router.push('/shops/' + m.id)">
        <div class="store-cover">
          <img class="store-cover-img" :src="imgSrc(m.coverUrl, m.category || route.params.key || 'food', 'shop-' + m.id, 'shop')" :data-seed="'shop-' + m.id" :data-category="m.category || route.params.key" alt="" @error="onImgError" />
          {{ m.shopName }}
          <span v-if="m.open === false || m.onlineStatus === 'OFFLINE'" class="cover-tag">休息中</span>
          <span v-else-if="m.inRange === false" class="cover-tag">超配送范围</span>
        </div>
        <div class="store-body">
          <b>{{ m.shopName }}</b>
          <div class="muted">{{ m.address }}{{ m.distanceKm != null ? ' · ' + m.distanceKm + ' km' : '' }}</div>
          <span v-if="m.open === false || m.onlineStatus === 'OFFLINE'" class="pill off">休息中</span>
          <span v-else-if="m.inRange === false" class="pill off">超配送范围不可下单</span>
          <span v-else class="pill">{{ (m.rating || 4.8) }} 分</span>
        </div>
      </article>
      <button v-if="hasNext" class="btn ghost" style="width:100%;margin-bottom:16px" :disabled="loading" @click="load(false)">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
      <p v-if="!merchants.length && !loading" class="muted">该分类暂时没有店铺</p>
    </div>
  </div>
</template>
