<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StarPicker from '../components/StarPicker.vue'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'
import { yuan } from '../brand'
import { snapshotOf } from '../status'
import { api, upload } from '../api'
import { imgSrc, onImgError } from '../img'
import { toast } from '../session'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const score = ref(0)
const riderScore = ref(0)
const content = ref('')
const photos = ref([])
const busy = ref(false)
const uploading = ref(false)
const loading = ref(true)
const loadError = ref('')

const snap = computed(() => snapshotOf(order.value))
const items = computed(() => snap.value.items || [])
const shopName = computed(() => snap.value.shopName || '本店')
const hasRider = computed(() => !!order.value?.riderId)
const canSubmit = computed(() => score.value >= 1 && (!hasRider.value || riderScore.value >= 1) && !busy.value)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    order.value = (await api(`/api/orders/${route.params.id}`)).data
    if (order.value.status !== 'COMPLETED') {
      toast('订单完成后才能评价', 'err')
      router.replace('/orders/' + route.params.id)
      return
    }
    if (order.value.reviewed) {
      toast('该订单已评价')
      router.replace('/orders/' + route.params.id)
    }
  } catch (e) {
    loadError.value = e.message || '订单加载失败'
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

async function addPhoto(ev) {
  const file = ev.target.files?.[0]
  ev.target.value = ''
  if (!file) return
  if (photos.value.length >= 3) return toast('最多 3 张配图', 'err')
  uploading.value = true
  try {
    const res = await upload('/api/review-photos', file)
    if (!res?.ok || !res.data?.photoUrl) throw new Error(res?.message || '上传失败')
    photos.value = photos.value.concat(res.data.photoUrl)
  } catch (e) {
    toast(e.message || '上传失败', 'err')
  } finally {
    uploading.value = false
  }
}

function removePhoto(i) {
  photos.value = photos.value.filter((_, idx) => idx !== i)
}

async function submit() {
  if (!canSubmit.value) {
    if (score.value < 1) return toast('请给商家点星', 'err')
    if (hasRider.value && riderScore.value < 1) return toast('请给骑手点星', 'err')
    return
  }
  busy.value = true
  try {
    const skuIds = items.value.map((it) => it.skuId).filter(Boolean)
    const body = {
      score: score.value,
      content: content.value,
      skuIds,
      photoUrls: photos.value,
      riderScore: hasRider.value ? riderScore.value : undefined
    }
    await api(`/api/orders/${route.params.id}/review`, { method: 'POST', body })
    toast('评价已提交')
    router.replace('/orders/' + route.params.id)
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="phone page">
    <PageHeader :back="'/orders/' + route.params.id" title="评价订单" subtitle="商家必选星，骑手只看配送" />
    <div class="phone-body has-bar">
      <div v-if="order" class="pad review">
        <section class="card">
          <div class="kicker">本单商品</div>
          <b class="t-h3 mt-1">{{ shopName }}</b>
          <div v-for="it in items.slice(0, 3)" :key="it.skuId" class="review-item">
            <img :src="imgSrc(it.imageUrl, 'food', 'review-sku-' + it.skuId, 'dish')" alt="" :data-seed="'review-sku-' + it.skuId" data-category="food" @error="onImgError" />
            <div class="grow ellipsis">{{ it.name }} × {{ it.qty }}</div>
            <span class="muted num">{{ yuan(it.priceCents) }}</span>
          </div>
        </section>

        <section class="card score-card">
          <b>商家评分</b>
          <p class="muted">点亮星星，1–5 星必选</p>
          <StarPicker v-model="score" />
          <div class="score-now">{{ score ? score + ' 星' : '还未点星' }}</div>
        </section>

        <section class="card">
          <b>想说的话</b>
          <textarea v-model="content" class="textarea mt-2" maxlength="512" rows="4" placeholder="口味、包装、分量都可以写（选填）"></textarea>
          <div class="muted mt-3 mb-2">配图最多 3 张（选填）</div>
          <div class="review-photos">
            <div v-for="(url, i) in photos" :key="url" class="photo-slot">
              <img :src="url" alt="" @error="onImgError" />
              <button type="button" class="photo-x" aria-label="移除配图" @click="removePhoto(i)"><Icon name="close" :size="12" :stroke="2.6" /></button>
            </div>
            <label v-if="photos.length < 3" class="photo-add">
              <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden @change="addPhoto" />
              <span v-if="uploading" class="spinner"></span>
              <Icon v-else name="camera" :size="20" />
              <small>{{ uploading ? '上传中' : '添加' }}</small>
            </label>
          </div>
        </section>

        <section v-if="hasRider" class="card score-card">
          <b>配送满意度</b>
          <p class="muted">只给骑手点星，不用写文字</p>
          <StarPicker v-model="riderScore" />
          <div class="score-now">{{ riderScore ? riderScore + ' 星' : '还未点星' }}</div>
        </section>
      </div>

      <div v-else-if="loadError" class="pad">
        <EmptyState kind="error" title="订单加载失败" :hint="loadError">
          <button class="btn btn-sm" type="button" @click="load"><Icon name="refresh" :size="16" />重试</button>
        </EmptyState>
      </div>

      <div v-else class="pad review" aria-busy="true" aria-label="加载中">
        <div class="skel" style="height:140px"></div>
        <div class="skel mt-3" style="height:120px"></div>
        <div class="skel mt-3" style="height:160px"></div>
      </div>
    </div>
    <div v-if="order" class="action-bar">
      <button class="btn btn-lg btn-block" type="button" :disabled="!canSubmit" @click="submit">
        <span v-if="busy" class="spinner"></span>
        {{ busy ? '提交中…' : '提交评价' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.review { padding-top: var(--s-4); display: flex; flex-direction: column; gap: 12px; }
.review-item { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.review-item img { width: 48px; height: 48px; border-radius: 12px; object-fit: cover; background: var(--ink-150); }
.score-card { text-align: center; }
.score-card .muted { margin: 4px 0 8px; }
.score-now { margin-top: 8px; font-size: var(--fs-sm); font-weight: 700; color: var(--accent-strong); }
.review-photos { display: flex; gap: 8px; }
.photo-slot, .photo-add {
  position: relative;
  width: 84px; height: 84px;
  border-radius: 14px;
  overflow: hidden;
  background: var(--surface-sunken);
}
.photo-slot img { width: 100%; height: 100%; object-fit: cover; }
.photo-x {
  position: absolute; top: 4px; right: 4px;
  width: 22px; height: 22px;
  display: grid; place-items: center;
  border-radius: 50%;
  background: rgba(12, 11, 10, 0.72);
  color: #fff;
}
.photo-add {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  color: var(--text-2);
  box-shadow: inset 0 0 0 1px var(--line-strong);
  cursor: pointer;
}
.photo-add small { font-size: var(--fs-2xs); }
</style>
