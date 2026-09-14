<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { waitForMatch } from '../poll'
import { matchSeckillOrder, seckillButtonLabel } from '../seckill'
import { goBack, toast } from '../session'

const route = useRoute()
const router = useRouter()
const activity = ref(null)
const now = ref(Date.now())
const busySku = ref(null)
const waiting = ref(false)
const myOrderId = ref(null)
let timer
let rotateTimer

const current = computed(() => activity.value?.currentSku || activity.value?.skus?.[0])
const grabbed = computed(() => !!(current.value?.grabbed || myOrderId.value || current.value?.orderId))
const remain = computed(() => Number(current.value?.remainStock ?? current.value?.originStock ?? 0))
const soldOut = computed(() => remain.value <= 0 && !grabbed.value)
const btnLabel = computed(() => seckillButtonLabel({
  busy: busySku.value != null,
  soldOut: soldOut.value,
  grabbed: grabbed.value,
  remain: remain.value
}))
const rotateLeft = computed(() => {
  const at = activity.value?.nextRotateAt ? new Date(activity.value.nextRotateAt).getTime() : 0
  const ms = Math.max(0, at - now.value)
  const m = Math.floor(ms / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})
const endLeft = computed(() => {
  const end = activity.value?.endAt ? new Date(activity.value.endAt).getTime() : now.value
  const ms = Math.max(0, end - now.value)
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return `${h} 时 ${m} 分 ${s} 秒`
})

async function load(forceRotate = false) {
  const prevSku = current.value?.skuId
  const data = (await api('/api/activities/' + route.params.id)).data
  if (forceRotate && data?.currentSku && data.currentSku.skuId !== prevSku) {
    myOrderId.value = data.currentSku.orderId || null
  } else if (!forceRotate && prevSku && data?.currentSku?.skuId === prevSku) {
    if (myOrderId.value && !data.currentSku.orderId) data.currentSku.orderId = myOrderId.value
    if (myOrderId.value || data.currentSku.grabbed) data.currentSku.grabbed = true
  }
  activity.value = data
  if (data?.currentSku?.orderId) myOrderId.value = data.currentSku.orderId
  scheduleRotate()
}

function scheduleRotate() {
  if (rotateTimer) clearTimeout(rotateTimer)
  const at = activity.value?.nextRotateAt ? new Date(activity.value.nextRotateAt).getTime() : 0
  const wait = Math.max(800, at - Date.now() + 400)
  if (at) {
    rotateTimer = setTimeout(async () => {
      try { await load(true) } catch { /* 下一档刷新失败可稍后重试 */ }
    }, Math.min(wait, 120000))
  }
}

onMounted(async () => {
  try {
    await load(true)
  } catch (e) {
    toast(e.message, 'err')
  }
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})
onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(rotateTimer)
})
watch(() => route.params.id, () => load(true))

async function goPay(orderId) {
  if (!orderId) return
  router.push('/orders/' + orderId + '/pay')
}

async function buy(sku) {
  if (busySku.value) return
  if (grabbed.value) return goPay(myOrderId.value || current.value?.orderId)
  if (soldOut.value) return toast('已售罄', 'err')
  busySku.value = sku.skuId
  waiting.value = false
  try {
    const res = await api(`/api/activities/${activity.value.id}/seckill`, { method: 'POST', body: { skuId: sku.skuId } })
    const data = res.data || {}
    const key = data.idempotencyKey
    if (typeof sku.remainStock === 'number' && !res.replayed && sku.remainStock > 0) {
      sku.remainStock -= 1
    }
    sku.grabbed = true
    if (res.replayed && data.orderId) {
      myOrderId.value = data.orderId
      toast('已抢过，正在打开订单')
      return goPay(data.orderId)
    }
    toast(res.replayed ? '已抢过，正在打开订单' : '秒杀成功')
    waiting.value = true
    const hit = await waitForMatch(
      async () => {
        if (data.orderId) {
          try { return [(await api('/api/orders/' + data.orderId)).data] } catch { return [] }
        }
        return (await api('/api/orders?size=20')).data?.items || []
      },
      (o) => !!(data.orderId
        ? String(o.id) === String(data.orderId)
        : (key && o.idempotencyKey === key)),
      { tries: 12, delayMs: 500 }
    )
    const order = matchSeckillOrder(hit ? [hit] : [], {
      orderId: data.orderId,
      idempotencyKey: key,
      skuId: sku.skuId,
      activityId: activity.value.id
    }) || hit
    if (order?.id) {
      myOrderId.value = order.id
      sku.orderId = order.id
      if (res.replayed) return goPay(order.id)
    } else if (res.replayed) {
      toast('未找到本档订单，请稍后在订单页查看', 'err')
    }
  } catch (e) {
    const msg = e.message || ''
    if (msg.includes('售罄') || msg.includes('库存')) toast('已售罄', 'err')
    else toast(msg, 'err')
  } finally {
    busySku.value = null
    waiting.value = false
  }
}

function stockText(sku) {
  const n = sku.remainStock ?? sku.originStock
  if (n === 0) return '已售罄'
  return n == null ? '参考库存' : `剩余 ${n}`
}
</script>

<template>
  <div class="phone page">
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router, '/home')">← 返回</button>
      <b class="page-title" style="font-size:18px">限时秒杀</b>
    </header>
    <div class="phone-body no-tab pad" v-if="activity && current">
      <div class="act-hero">
        <img
          :src="imgSrc(current.imageUrl, 'fresh', 'act-' + current.skuId, 'dish')"
          alt=""
          data-category="fresh"
          :data-seed="'act-' + current.skuId"
          @error="onImgError"
        />
      </div>
      <div class="card" style="margin-top:12px">
        <span class="pill">距结束 {{ endLeft }}</span>
        <h2 style="margin:8px 0 4px;font-size:20px">{{ current.name }}</h2>
        <div class="muted">下一档还剩 {{ rotateLeft }} · 按档期轮换，抢完不换品</div>
        <div class="act-price">
          <b>{{ yuan(current.priceCents) }}</b>
          <span class="muted" style="text-decoration:line-through">{{ yuan(current.originPriceCents) }}</span>
          <span class="muted">{{ stockText(current) }}</span>
        </div>
        <p v-if="waiting" class="muted">订单由活动消息落地，正在等待…</p>
        <button
          class="btn"
          style="width:100%;margin-top:12px"
          :disabled="busySku != null || soldOut"
          @click="buy(current)"
        >
          {{ btnLabel }}
        </button>
      </div>
    </div>
    <p v-else class="pad muted">活动加载中…</p>
  </div>
</template>
