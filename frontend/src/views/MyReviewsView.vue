<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import MemberBadge from '../components/MemberBadge.vue'
import StarPicker from '../components/StarPicker.vue'
import { api } from '../api'
import { onImgError } from '../img'
import { goBack, toast } from '../session'

const router = useRouter()
const items = ref([])
const page = ref(1)
const hasNext = ref(false)
const loading = ref(false)

async function load(reset) {
  if (loading.value) return
  loading.value = true
  try {
    const p = reset ? 1 : page.value
    const res = await api(`/api/me/reviews?page=${p}&size=10`)
    const rows = res.data?.items || []
    items.value = reset ? rows : items.value.concat(rows)
    hasNext.value = !!res.data?.hasNext
    page.value = p + 1
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

function timeText(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(() => load(true))
</script>

<template>
  <div class="phone page">
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router, '/me')">← 返回</button>
      <b>我的评价</b>
    </header>
    <div class="phone-body pad">
      <article
        v-for="r in items"
        :key="r.id"
        class="card"
        style="margin-bottom:10px;cursor:pointer"
        @click="router.push('/shops/' + r.merchantId + '?reviewId=' + r.id)"
      >
        <div class="row" style="justify-content:space-between">
          <b>{{ r.shopName || '店铺' }}</b>
          <StarPicker :model-value="r.score || 0" size="sm" readonly />
        </div>
        <div class="row" style="margin-top:6px;gap:8px">
          <MemberBadge :member="r.member" compact />
          <span class="muted">{{ timeText(r.createdAt) }} · 赞 {{ r.likeCount || 0 }}</span>
        </div>
        <div v-if="r.content" style="margin-top:8px">{{ r.content }}</div>
        <div v-if="r.photoUrls?.length" class="review-photos" style="margin-top:8px">
          <img v-for="url in r.photoUrls" :key="url" :src="url" alt="" class="review-thumb" @error="onImgError" />
        </div>
        <div class="muted" style="margin-top:6px">{{ r.skuNames }}</div>
      </article>
      <button v-if="hasNext" class="btn ghost" style="width:100%" :disabled="loading" @click="load(false)">加载更多</button>
      <p v-if="!items.length && !loading" class="muted">还没有评价，完成订单后可从订单详情去评价。</p>
    </div>
    <TabBar />
  </div>
</template>
