<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yuan } from '../brand'
import { api } from '../api'
import { imgSrc, onImgError } from '../img'
import { waitForMatch } from '../poll'
import { matchSeckillOrder, seckillButtonLabel } from '../seckill'
import { toast } from '../session'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'

const route = useRoute()
const router = useRouter()
const activity = ref(null)
const now = ref(Date.now())
const busySku = ref(null)
const waiting = ref(false)
const myOrderId = ref(null)
const loading = ref(true)
const loadError = ref('')
let timer
let rotateTimer

const current = computed(() => activity.value?.currentSku || activity.value?.skus?.[0])
const grabbed = computed(() => !!(current.value?.grabbed || myOrderId.value || current.value?.orderId))
const remain = computed(() => Number(current.value?.remainStock ?? current.value?.originStock ?? 0))
const origin = computed(() => Number(current.value?.originStock || 0))
const soldOut = computed(() => remain.value <= 0 && !grabbed.value)
const stockPct = computed(() => {
  if (!origin.value) return remain.value > 0 ? 100 : 0
  return Math.max(0, Math.min(100, Math.round((remain.value / origin.value) * 100)))
})
const btnLabel = computed(() => seckillButtonLabel({
  busy: busySku.value != null,
  soldOut: soldOut.value,
  grabbed: grabbed.value,
  remain: remain.value
}))
const rotateLeft = computed(() => splitLeft(activity.value?.nextRotateAt))
const endLeft = computed(() => splitLeft(activity.value?.endAt, true))

function splitLeft(at, withDays = false) {
  const end = at ? new Date(at).getTime() : 0
  const ms = Math.max(0, end - now.value)
  const days = Math.floor(ms / 86400000)
  const h = Math.floor((ms % 86400000) / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  const parts = [h, m, s].map((n) => String(n).padStart(2, '0'))
  return { days: withDays ? days : 0, parts }
}

async function load(forceRotate = false) {
  const prevSku = current.value?.skuId
  try {
    const data = (await api('/api/activities/' + route.params.id)).data
    if (forceRotate && data?.currentSku && data.currentSku.skuId !== prevSku) {
      myOrderId.value = data.currentSku.orderId || null
    } else if (!forceRotate && prevSku && data?.currentSku?.skuId === prevSku) {
      if (myOrderId.value && !data.currentSku.orderId) data.currentSku.orderId = myOrderId.value
      if (myOrderId.value || data.currentSku.grabbed) data.currentSku.grabbed = true
    }
    activity.value = data
    if (data?.currentSku?.orderId) myOrderId.value = data.currentSku.orderId
    loadError.value = ''
    scheduleRotate()
  } catch (e) {
    if (!activity.value) loadError.value = e.message || '活动加载失败'
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
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
  await load(true)
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})
onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(rotateTimer)
})
watch(() => route.params.id, () => {
  loading.value = true
  load(true)
})

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
</script>

<template>
  <div class="phone page">
    <PageHeader back="/home" title="限时秒杀" subtitle="手慢无，卖完即止" />
    <div class="phone-body has-bar">
      <div v-if="current" class="pad act">
        <section class="act-hero">
          <img
            :src="imgSrc(current.imageUrl, 'fresh', 'act-' + current.skuId, 'dish')"
            alt=""
            data-category="fresh"
            :data-seed="'act-' + current.skuId"
            @error="onImgError"
          />
          <div class="act-shade">
            <span class="sk-badge"><Icon name="flame" :size="14" :stroke="2.2" />本档在售</span>
            <h1>{{ current.name }}</h1>
            <p>下一档 {{ rotateLeft.parts.join(':') }} 后轮换，本档售罄不会提前换品</p>
          </div>
        </section>

        <section class="act-panel">
          <div class="act-price">
            <b class="num">{{ yuan(current.priceCents) }}</b>
            <s v-if="current.originPriceCents" class="num">{{ yuan(current.originPriceCents) }}</s>
            <span class="pill">{{ soldOut ? '已售罄' : `剩余 ${remain}` }}</span>
          </div>
          <div class="act-stock">
            <div class="row-between">
              <span>本档库存</span>
              <span class="num">{{ remain }}<small v-if="origin"> / {{ origin }}</small></span>
            </div>
            <div class="progress" role="progressbar" :aria-valuenow="stockPct" aria-valuemin="0" aria-valuemax="100" aria-label="剩余库存">
              <i :style="{ width: stockPct + '%' }"></i>
            </div>
          </div>
          <div class="act-clock" aria-label="距活动结束">
            <span>距结束</span>
            <em v-if="endLeft.days" class="num">{{ endLeft.days }}天</em>
            <i class="num">{{ endLeft.parts[0] }}</i>
            <b>:</b>
            <i class="num">{{ endLeft.parts[1] }}</i>
            <b>:</b>
            <i class="num">{{ endLeft.parts[2] }}</i>
          </div>
          <p v-if="waiting" class="act-wait"><span class="spinner"></span>秒杀成功，正在生成订单…</p>
          <ul class="act-notes">
            <li><Icon name="bolt" :size="14" />手慢无，卖完即止</li>
            <li><Icon name="shield" :size="14" />重复点击也不会多扣</li>
          </ul>
        </section>
      </div>

      <div v-else-if="loadError" class="pad">
        <EmptyState kind="error" title="活动加载失败" :hint="loadError">
          <button class="btn btn-sm" type="button" @click="loading = true; load(true)"><Icon name="refresh" :size="16" />重试</button>
        </EmptyState>
      </div>

      <div v-else-if="!loading" class="pad">
        <EmptyState title="这一档还没有商品" hint="下一档会换上新的秒杀商品" />
      </div>

      <div v-else class="pad act" aria-busy="true" aria-label="加载中">
        <div class="skel" style="height:220px;border-radius:24px"></div>
        <div class="skel mt-3" style="height:180px;border-radius:20px"></div>
      </div>
    </div>

    <div v-if="current" class="action-bar">
      <div class="bar-sum">
        <small>{{ grabbed ? '已锁定本档资格' : '秒杀价不含配送费' }}</small>
        <b class="num">{{ yuan(current.priceCents) }}</b>
      </div>
      <button class="btn btn-lg grow" type="button" :disabled="busySku != null || soldOut" @click="buy(current)">
        <span v-if="busySku != null" class="spinner"></span>
        <Icon v-else name="flame" :size="16" :stroke="2.2" />
        {{ btnLabel }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.act { padding-top: var(--s-4); }
.act-hero {
  position: relative;
  height: 220px;
  border-radius: var(--r-xl);
  overflow: hidden;
  background: #1a120c;
  box-shadow: var(--sh-3);
  animation: rise var(--dur-4) var(--ease-out) both;
}
.act-hero img { width: 100%; height: 100%; object-fit: cover; }
.act-shade {
  position: absolute; inset: auto 0 0 0;
  padding: 28px 16px 16px;
  color: #fff;
  background: linear-gradient(180deg, transparent, rgba(18, 10, 6, 0.88));
}
.sk-badge {
  display: inline-flex; align-items: center; gap: 4px;
  height: 24px; padding: 0 8px;
  border-radius: var(--r-pill);
  background: var(--grad-brand);
  color: #1A1208;
  font-size: var(--fs-xs); font-weight: 800;
}
.act-shade h1 { margin-top: 8px; font-size: var(--fs-2xl); font-weight: 800; letter-spacing: -0.02em; overflow-wrap: anywhere; }
.act-shade p { margin-top: 4px; font-size: var(--fs-xs); color: rgba(255, 255, 255, 0.72); overflow-wrap: anywhere; }
.act-panel {
  margin-top: -18px;
  position: relative;
  padding: 18px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-2);
  animation: rise var(--dur-4) 80ms var(--ease-out) both;
}
.act-price { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; max-width: 100%; }
.act-price b { font-size: 32px; font-weight: 800; color: var(--flame-600); letter-spacing: -0.03em; }
.act-price s { font-size: var(--fs-sm); color: var(--text-3); }
.act-stock { margin-top: 14px; font-size: var(--fs-sm); color: var(--text-2); }
.act-stock .progress { height: 8px; margin-top: 8px; }
.act-stock small { font-weight: 650; color: var(--text-3); }
.act-clock {
  display: flex; align-items: center; gap: 4px; flex-wrap: wrap;
  margin-top: 14px;
  max-width: 100%;
  font-size: var(--fs-xs); font-weight: 700; color: var(--text-3);
}
.act-clock em { font-style: normal; margin-right: 2px; color: var(--text-2); white-space: nowrap; }
.act-clock i {
  font-style: normal;
  min-width: 28px; height: 26px;
  display: grid; place-items: center;
  border-radius: 7px;
  background: var(--ink-900); color: #fff;
  font-weight: 800;
}
.act-clock b { color: var(--text-3); }
.act-wait {
  display: flex; align-items: center; gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: var(--fs-sm); font-weight: 650;
}
.act-notes { list-style: none; margin: 14px 0 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.act-notes li { display: flex; align-items: flex-start; gap: 6px; min-width: 0; font-size: var(--fs-xs); color: var(--text-2); overflow-wrap: anywhere; }
.act-notes .ico { color: var(--accent); }
</style>
