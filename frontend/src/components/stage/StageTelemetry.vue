<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import Icon from '../Icon.vue'
import { api } from '../../api'
import { session } from '../../session'
import { shardOf, stage } from '../../stage'
import { orderStatusText } from '../../status'

const FLOW = ['CREATED', 'MERCHANT_PENDING', 'PAID', 'ACCEPTED', 'ARRIVED', 'DELIVERING', 'COMPLETED']
const FLOW_SHORT = {
  CREATED: '待支付',
  MERCHANT_PENDING: '待商家接单',
  PAID: '待骑手接单',
  ACCEPTED: '骑手已接单',
  ARRIVED: '骑手到店',
  DELIVERING: '配送中',
  COMPLETED: '已完成'
}

const latency = ref(null)
const gatewayOk = ref(null)
const provider = ref('')
const metrics = ref(null)
const active = ref(false)
let timer
let mq

async function ping() {
  const t0 = performance.now()
  try {
    const res = await api('/api/map/config')
    latency.value = Math.round(performance.now() - t0)
    gatewayOk.value = true
    provider.value = res.data?.provider === 'amap' ? '高德 JS API' : '栅格瓦片'
  } catch {
    gatewayOk.value = false
    latency.value = null
  }
}

async function loadMetrics() {
  if (!session.me) { metrics.value = null; return }
  try {
    metrics.value = (await api('/api/dispatch/route-metrics')).data || null
  } catch {
    metrics.value = null
  }
}

function tick() {
  if (!active.value || document.hidden) return
  ping()
  loadMetrics()
}

const engine = computed(() => {
  const e = metrics.value?.engine || {}
  const gds = Number(e.NEO4J_GDS) || 0
  const mem = Number(e.MEMORY_OSM) || 0
  if (!gds && !mem) return { label: '待首次算路', gds, mem }
  return { label: gds >= mem ? 'Neo4j GDS' : '内存 OSM 路网', gds, mem }
})
const algos = computed(() => {
  const a = metrics.value?.algorithm || {}
  const rows = [
    { key: 'A*', v: Number(a.ASTAR) || 0 },
    { key: 'Dijkstra', v: Number(a.DIJKSTRA) || 0 },
    { key: '直线兜底', v: Number(a.HAVERSINE_FALLBACK) || 0 }
  ]
  const max = Math.max(1, ...rows.map((r) => r.v))
  return rows.map((r) => ({ ...r, pct: Math.round((r.v / max) * 100) }))
})
const hitRate = computed(() => {
  const h = Number(metrics.value?.cacheHits) || 0
  const m = Number(metrics.value?.cacheMisses) || 0
  if (!h && !m) return null
  return Math.round((h / (h + m)) * 100)
})

const focus = computed(() => stage.order)
const flowIndex = computed(() => FLOW.indexOf(focus.value?.status))
const offFlow = computed(() => focus.value?.status && flowIndex.value < 0)
const shard = computed(() => shardOf(focus.value?.id))

function syncActive() {
  active.value = !!mq?.matches
  if (active.value) tick()
}

onMounted(() => {
  if (typeof window.matchMedia !== 'function') return
  mq = window.matchMedia('(min-width: 1240px)')
  mq.addEventListener?.('change', syncActive)
  syncActive()
  timer = setInterval(tick, 15000)
})
onUnmounted(() => {
  clearInterval(timer)
  mq?.removeEventListener?.('change', syncActive)
})
watch(() => session.me?.userId, () => { if (active.value) loadMetrics() })
watch(() => focus.value?.id, () => { if (active.value) loadMetrics() })
</script>

<template>
  <aside aria-label="系统实况">
    <div class="tm-head">
      <span class="tm-live"><i></i>LIVE</span>
      <b>系统实况</b>
    </div>

    <section class="tm-card">
      <div class="tm-card-head">
        <span><Icon name="server" :size="16" />服务拓扑</span>
        <em :class="{ ok: gatewayOk, bad: gatewayOk === false }">
          {{ gatewayOk == null ? '检测中' : gatewayOk ? `网关 ${latency} ms` : '网关不可达' }}
        </em>
      </div>
      <div class="topo">
        <div class="topo-node gw"><b>Gateway</b><span>:18080 · JWT 鉴权</span></div>
        <svg class="topo-lines" viewBox="0 0 300 36" preserveAspectRatio="none" aria-hidden="true">
          <path d="M150 0 V12 H50 V36 M150 12 V36 M150 12 H250 V36" />
        </svg>
        <div class="topo-row">
          <div class="topo-node"><b>account</b><span>:18081</span></div>
          <div class="topo-node"><b>activity</b><span>:18082</span></div>
          <div class="topo-node"><b>order</b><span>:18083</span></div>
        </div>
        <div class="topo-infra">
          <span><Icon name="database" :size="13" />MySQL</span>
          <span><Icon name="zap" :size="13" />Redis</span>
          <span><Icon name="layers" :size="13" />RabbitMQ</span>
        </div>
      </div>
      <p class="tm-note">地图底图：{{ provider || '—' }}；路径计算由 order 服务本地完成，不依赖地图厂商。</p>
    </section>

    <section class="tm-card">
      <div class="tm-card-head">
        <span><Icon name="route" :size="16" />路径规划引擎</span>
        <em>{{ metrics ? engine.label : (session.me ? '加载中' : '登录后可见') }}</em>
      </div>
      <div v-if="metrics" class="algo-bars">
        <div v-for="a in algos" :key="a.key" class="algo-row">
          <span>{{ a.key }}</span>
          <div class="algo-track"><i :style="{ width: a.pct + '%' }"></i></div>
          <b class="num">{{ a.v }}</b>
        </div>
        <div class="algo-foot">
          <span>路径缓存命中 <b class="num">{{ hitRate == null ? '—' : hitRate + '%' }}</b></span>
          <span>缓存 <b class="num">{{ metrics.cacheSize ?? 0 }}</b> 条</span>
        </div>
      </div>
      <div v-else class="tm-placeholder">骑手 → 商家 → 用户 两段最短路，计数来自 /api/dispatch/route-metrics</div>
    </section>

    <section class="tm-card">
      <div class="tm-card-head">
        <span><Icon name="activity" :size="16" />订单状态机</span>
        <em>{{ focus ? '#' + focus.id : '打开任一订单联动' }}</em>
      </div>
      <ol class="fsm">
        <li
          v-for="(s, i) in FLOW"
          :key="s"
          :class="{ done: flowIndex > i, current: flowIndex === i }"
        >
          <i></i><span>{{ FLOW_SHORT[s] }}</span>
        </li>
      </ol>
      <p v-if="offFlow" class="tm-note warn">当前处于分支状态：{{ orderStatusText(focus.status) }}</p>
      <p v-else class="tm-note">OrderStateMachine 集中约束合法迁移，终态禁止回迁。</p>
    </section>

    <section class="tm-card">
      <div class="tm-card-head">
        <span><Icon name="database" :size="16" />订单分片 4 库 × 8 表</span>
        <em>{{ shard ? `ds${shard.db} · t_order_${shard.table}` : 'id % 4 · (id/4) % 8' }}</em>
      </div>
      <div class="shard-grid" role="img" :aria-label="shard ? `订单落在 ssd_order_${shard.db}.t_order_${shard.table}` : '4 库 8 表分片示意'">
        <template v-for="db in 4" :key="db">
          <span class="shard-db">ds{{ db - 1 }}</span>
          <i
            v-for="t in 8"
            :key="t"
            :class="{ hit: shard && shard.db === db - 1 && shard.table === t - 1, row: shard && shard.db === db - 1 }"
          ></i>
        </template>
      </div>
      <p class="tm-note">ShardingSphere-JDBC 按 Leaf 号段生成的订单 ID 路由，共 32 张分片表。</p>
    </section>
  </aside>
</template>
