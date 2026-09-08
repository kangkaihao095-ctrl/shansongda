<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import StarPicker from '../components/StarPicker.vue'
import { yuan } from '../brand'
import { snapshotOf } from '../status'
import { api, upload } from '../api'
import { imgSrc, onImgError } from '../img'
import { goBack, session, toast } from '../session'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const score = ref(0)
const riderScore = ref(0)
const content = ref('')
const photos = ref([])
const busy = ref(false)
const uploading = ref(false)

const snap = computed(() => snapshotOf(order.value))
const items = computed(() => snap.value.items || [])
const shopName = computed(() => snap.value.shopName || '本店')
const hasRider = computed(() => !!order.value?.riderId)
const canSubmit = computed(() => score.value >= 1 && (!hasRider.value || riderScore.value >= 1) && !busy.value)

async function load() {
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
    toast(e.message, 'err')
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
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router, '/orders/' + route.params.id)">← 返回</button>
      <b>评价订单</b>
    </header>
    <div class="phone-body pad" v-if="order">
      <div class="card review-shop">
        <div class="muted">{{ shopName }}</div>
        <div v-for="it in items.slice(0, 3)" :key="it.skuId" class="row" style="margin-top:8px">
          <img :src="imgSrc(it.imageUrl, 'food', 'review-sku-' + it.skuId, 'dish')" alt="" :data-seed="'review-sku-' + it.skuId" data-category="food" @error="onImgError" class="review-thumb" />
          <div style="flex:1">{{ it.name }} × {{ it.qty }}</div>
          <span class="muted">{{ yuan(it.priceCents) }}</span>
        </div>
      </div>
      <div class="card" style="margin-top:12px;text-align:center">
        <b>商家评分</b>
        <p class="muted" style="margin:6px 0 4px">点亮星星，1–5 星必选</p>
        <StarPicker v-model="score" />
        <div class="muted" style="margin-top:6px">{{ score ? score + ' 星' : '还未点星' }}</div>
      </div>
      <div class="card" style="margin-top:12px">
        <b>想说的话</b>
        <textarea v-model="content" class="review-text" maxlength="512" rows="4" placeholder="口味、包装、分量都可以写（选填）"></textarea>
        <div class="muted" style="margin-bottom:8px">配图最多 3 张（选填）</div>
        <div class="review-photos">
          <div v-for="(url, i) in photos" :key="url" class="photo-slot">
            <img :src="url" alt="" @error="onImgError" />
            <button type="button" class="photo-x" @click="removePhoto(i)">×</button>
          </div>
          <label v-if="photos.length < 3" class="photo-add">
            <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden @change="addPhoto" />
            {{ uploading ? '上传中' : '+' }}
          </label>
        </div>
      </div>
      <div v-if="hasRider" class="card" style="margin-top:12px;text-align:center">
        <b>配送满意度</b>
        <p class="muted" style="margin:6px 0 4px">只给骑手点星，不用写文字</p>
        <StarPicker v-model="riderScore" />
        <div class="muted" style="margin-top:6px">{{ riderScore ? riderScore + ' 星' : '还未点星' }}</div>
      </div>
      <button class="btn" style="width:100%;margin-top:16px" :disabled="!canSubmit" @click="submit">
        {{ busy ? '提交中…' : '提交评价' }}
      </button>
    </div>
    <TabBar />
  </div>
</template>
