<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import MemberBadge from '../components/MemberBadge.vue'
import StarPicker from '../components/StarPicker.vue'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'
import { api } from '../api'
import { onImgError } from '../img'
import { toast } from '../session'

const router = useRouter()
const items = ref([])
const page = ref(1)
const hasNext = ref(false)
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')

async function load(reset) {
  if (loading.value) return
  loading.value = true
  if (reset) loadError.value = ''
  try {
    const p = reset ? 1 : page.value
    const res = await api(`/api/me/reviews?page=${p}&size=10`)
    const rows = res.data?.items || []
    items.value = reset ? rows : items.value.concat(rows)
    hasNext.value = !!res.data?.hasNext
    page.value = p + 1
  } catch (e) {
    loadError.value = e.message || '评价加载失败'
    toast(e.message, 'err')
  } finally {
    loading.value = false
    loaded.value = true
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
    <PageHeader back="/me" title="我的评价" subtitle="点进店铺可看到这条评价" />
    <div class="phone-body pad">
      <div v-if="!loaded" class="stack-3" aria-busy="true" aria-label="加载中">
        <div v-for="n in 3" :key="n" class="skel" style="height:120px;border-radius:18px"></div>
      </div>
      <EmptyState v-else-if="loadError && !items.length" kind="error" title="评价加载失败" :hint="loadError">
        <button class="btn btn-sm" type="button" @click="load(true)"><Icon name="refresh" :size="16" />重试</button>
      </EmptyState>
      <template v-else>
        <article
          v-for="r in items"
          :key="r.id"
          class="review-card"
          role="link"
          tabindex="0"
          @click="router.push('/shops/' + r.merchantId + '?reviewId=' + r.id)"
          @keydown.enter="router.push('/shops/' + r.merchantId + '?reviewId=' + r.id)"
        >
          <div class="row-between">
            <b class="ellipsis">{{ r.shopName || '店铺' }}</b>
            <StarPicker :model-value="r.score || 0" size="sm" readonly />
          </div>
          <div class="row mt-2">
            <MemberBadge :member="r.member" compact />
            <span class="muted">{{ timeText(r.createdAt) }} · 赞 {{ r.likeCount || 0 }}</span>
          </div>
          <p v-if="r.content" class="review-text">{{ r.content }}</p>
          <div v-if="r.photoUrls?.length" class="review-photos">
            <img v-for="url in r.photoUrls" :key="url" :src="url" alt="" @error="onImgError" />
          </div>
          <div v-if="r.skuNames" class="muted mt-2 ellipsis">{{ r.skuNames }}</div>
        </article>
        <button v-if="hasNext" class="btn ghost btn-block mt-3" type="button" :disabled="loading" @click="load(false)">
          <span v-if="loading" class="spinner"></span>{{ loading ? '加载中…' : '加载更多' }}
        </button>
        <EmptyState v-if="!items.length" title="还没有评价" hint="完成订单后，可以从订单详情去评价">
          <button class="btn btn-sm" type="button" @click="router.push('/orders')">去看订单</button>
        </EmptyState>
      </template>
    </div>
    <TabBar />
  </div>
</template>

<style scoped>
.review-card {
  margin-top: var(--s-3);
  padding: 14px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-1);
  cursor: pointer;
  text-align: left;
  transition: transform var(--dur-2) var(--ease-out), box-shadow var(--dur-2) ease;
}
.review-card:first-child { margin-top: var(--s-4); }
.review-card:active { transform: scale(0.99); }
.review-text { margin-top: 8px; font-size: var(--fs-base); line-height: 1.55; }
.review-photos { display: flex; gap: 6px; margin-top: 8px; }
.review-photos img { width: 64px; height: 64px; border-radius: 12px; object-fit: cover; background: var(--ink-150); }
</style>
