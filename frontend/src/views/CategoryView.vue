<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CATEGORIES, categoryName } from '../catalog'
import { api } from '../api'
import { toast } from '../session'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StoreCard from '../components/StoreCard.vue'
import Icon from '../components/Icon.vue'

const route = useRoute()
const router = useRouter()
const merchants = ref([])
const page = ref(1)
const hasNext = ref(false)
const loading = ref(false)
const loadError = ref('')
const cat = computed(() => CATEGORIES.find((c) => c.key === route.params.key))

async function load(reset) {
  if (loading.value) return
  loading.value = true
  if (reset) loadError.value = ''
  try {
    const nextPage = reset ? 1 : page.value
    const res = await api(`/api/merchants?category=${route.params.key}&page=${nextPage}&size=15`)
    const items = res.data?.items || []
    merchants.value = reset ? items : merchants.value.concat(items)
    hasNext.value = !!res.data?.hasNext
    page.value = nextPage + 1
  } catch (e) {
    if (reset) loadError.value = e.message || '加载失败'
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
    <PageHeader back="/home" :title="cat?.name || categoryName(route.params.key)" :subtitle="merchants.length ? `已为你找到 ${merchants.length}${hasNext ? '+' : ''} 家店铺` : '按推荐分与距离排序'" />
    <div class="phone-body no-tab">
      <nav class="cat-strip" aria-label="切换品类">
        <button
          v-for="c in CATEGORIES"
          :key="c.key"
          class="cat-pill"
          :class="{ on: c.key === route.params.key }"
          type="button"
          :aria-current="c.key === route.params.key ? 'page' : undefined"
          @click="router.replace('/category/' + c.key)"
        >
          <img :src="c.image" alt="" loading="lazy" />
          <span>{{ c.name }}</span>
        </button>
      </nav>

      <div class="pad">
        <div v-if="loading && !merchants.length" aria-busy="true">
          <div v-for="n in 3" :key="n" class="skel-card">
            <div class="skel" style="height:148px;border-radius:14px"></div>
            <div class="skel skel-line mt-3" style="width:56%"></div>
            <div class="skel skel-line mt-2" style="width:78%"></div>
          </div>
        </div>

        <EmptyState v-else-if="loadError && !merchants.length" kind="error" title="加载失败" :hint="loadError">
          <button class="btn btn-sm" type="button" @click="load(true)"><Icon name="refresh" :size="16" />重新加载</button>
        </EmptyState>

        <div v-else class="cat-list">
          <StoreCard
            v-for="(m, i) in merchants"
            :key="m.id"
            v-reveal="(i % 15) * 40"
            :shop="m"
            :category="route.params.key"
            @open="router.push('/shops/' + m.id)"
          />
        </div>

        <button v-if="hasNext" class="btn ghost btn-block mt-4" type="button" :disabled="loading" @click="load(false)">
          <span v-if="loading" class="spinner"></span>{{ loading ? '加载中…' : '加载更多店铺' }}
        </button>
        <EmptyState v-if="!merchants.length && !loading && !loadError" kind="offline" title="该分类暂时没有店铺" hint="换个品类或回到首页看看推荐">
          <button class="btn btn-sm ghost" type="button" @click="router.push('/home')">回到首页</button>
        </EmptyState>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cat-strip {
  display: flex; gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  padding: 14px var(--gutter) 12px;
}
.cat-strip::-webkit-scrollbar { display: none; }
.cat-pill {
  flex-shrink: 0;
  display: inline-flex; align-items: center; gap: 6px;
  height: 38px; padding: 0 14px 0 4px;
  border-radius: var(--r-pill);
  background: var(--surface);
  box-shadow: var(--sh-1);
  font-size: var(--fs-sm); font-weight: 650; color: var(--text-2);
  transition: background var(--dur-2) ease, color var(--dur-2) ease, box-shadow var(--dur-2) ease, transform var(--dur-1) ease;
}
.cat-pill:active { transform: scale(0.96); }
.cat-pill img { width: 30px; height: 30px; border-radius: 50%; object-fit: cover; }
.cat-pill.on { background: var(--ink-900); color: #fff; box-shadow: var(--sh-2); }
.cat-list { display: flex; flex-direction: column; gap: var(--s-3); }
</style>
