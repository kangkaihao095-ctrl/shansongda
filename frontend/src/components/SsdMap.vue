<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { api } from '../api'

const props = defineProps({
  points: { type: Array, default: () => [] },
  rider: Object,
  merchant: Object,
  user: Object,
  eta: String,
  tall: Boolean
})

const AMAP_WEBRD = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const AMAP_VEC = 'https://wprd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&style=7&x={x}&y={y}&z={z}&scl=1&ltype=0'
const OSM = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'

const box = ref(null)
let map
let provider = 'amap-tiles'
let resizeObs
let sizeTimers = []

function loadScript(src) {
  return new Promise((resolve, reject) => {
    if ([...document.scripts].some((s) => s.src === src)) return resolve()
    const el = document.createElement('script')
    el.src = src
    el.onload = resolve
    el.onerror = reject
    document.head.appendChild(el)
  })
}

function loadCss(href) {
  if ([...document.styleSheets].some((s) => s.href === href)) return
  const el = document.createElement('link')
  el.rel = 'stylesheet'
  el.href = href
  document.head.appendChild(el)
}

function allPoints() {
  const pts = []
  if (props.rider) pts.push([props.rider.lat, props.rider.lon])
  ;(props.points || []).forEach((p) => pts.push([p.lat, p.lon]))
  if (props.merchant) pts.push([props.merchant.lat, props.merchant.lon])
  if (props.user) pts.push([props.user.lat, props.user.lon])
  return pts.filter((p) => Number.isFinite(p[0]) && Number.isFinite(p[1]))
}

function clearSizeTimers() {
  sizeTimers.forEach((id) => clearTimeout(id))
  sizeTimers = []
}

function scheduleInvalidate() {
  clearSizeTimers()
  const bump = () => {
    if (map && typeof map.invalidateSize === 'function') map.invalidateSize()
    if (map && typeof map.resize === 'function') map.resize()
  }
  nextTick(bump)
  sizeTimers.push(setTimeout(bump, 80), setTimeout(bump, 320), setTimeout(bump, 800))
}

function addAmapTiles(L, target) {
  const layers = [
    { url: AMAP_WEBRD, opts: { subdomains: '1234', attribution: '高德地图' } },
    { url: AMAP_VEC, opts: { subdomains: '1234', attribution: '高德地图' } },
    { url: OSM, opts: { attribution: '© OpenStreetMap' } }
  ]
  let current
  const use = (idx) => {
    if (current) target.removeLayer(current)
    current = L.tileLayer(layers[idx].url, { ...layers[idx].opts, maxZoom: 18 }).addTo(target)
    let fails = 0
    current.on('tileerror', () => {
      fails += 1
      if (fails >= 4 && idx + 1 < layers.length) use(idx + 1)
    })
  }
  use(0)
}

async function drawLeaflet() {
  loadCss('https://unpkg.com/leaflet@1.9.4/dist/leaflet.css')
  await loadScript('https://unpkg.com/leaflet@1.9.4/dist/leaflet.js')
  const L = window.L
  const pts = allPoints()
  const center = pts[0] || [31.2304, 121.4737]
  if (map && map.remove) map.remove()
  map = L.map(box.value, { zoomControl: false }).setView(center, 15)
  addAmapTiles(L, map)
  if (pts.length > 1) {
    L.polyline(pts, { color: '#FF6A00', weight: 5, opacity: 0.9 }).addTo(map)
    map.fitBounds(pts, { padding: [24, 24] })
  }
  if (props.rider) {
    L.marker([props.rider.lat, props.rider.lon], { icon: pin(L, '/images/markers/rider.svg', [40, 44], [20, 40]) })
      .addTo(map).bindTooltip('骑手')
  }
  if (props.merchant) {
    L.marker([props.merchant.lat, props.merchant.lon], { icon: pin(L, '/images/markers/shop.svg', [32, 42], [16, 40]) })
      .addTo(map).bindTooltip('商家')
  }
  if (props.user) {
    L.marker([props.user.lat, props.user.lon], { icon: pin(L, '/images/markers/user.svg', [32, 42], [16, 40]) })
      .addTo(map).bindTooltip('收货')
  }
  scheduleInvalidate()
}

function pin(L, url, size, anchor) {
  return L.icon({
    iconUrl: url,
    iconSize: size,
    iconAnchor: anchor,
    tooltipAnchor: [0, -anchor[1] + 8]
  })
}

function amapIcon(AMap, url, w, h) {
  return new AMap.Icon({
    size: new AMap.Size(w, h),
    image: url,
    imageSize: new AMap.Size(w, h)
  })
}

async function drawAmap(key) {
  await loadScript(`https://webapi.amap.com/maps?v=2.0&key=${key}`)
  const AMap = window.AMap
  const pts = allPoints()
  const center = pts[0] || [31.2304, 121.4737]
  if (map && map.destroy) map.destroy()
  map = new AMap.Map(box.value, { zoom: 15, center: [center[1], center[0]], mapStyle: 'amap://styles/normal' })
  if (pts.length > 1) {
    const line = new AMap.Polyline({
      path: pts.map((p) => [p[1], p[0]]),
      strokeColor: '#FF6A00',
      strokeWeight: 6
    })
    map.add(line)
    map.setFitView([line])
  }
  if (props.rider) {
    map.add(new AMap.Marker({
      position: [props.rider.lon, props.rider.lat],
      title: '骑手',
      icon: amapIcon(AMap, '/images/markers/rider.svg', 40, 44),
      offset: new AMap.Pixel(-20, -40)
    }))
  }
  if (props.merchant) {
    map.add(new AMap.Marker({
      position: [props.merchant.lon, props.merchant.lat],
      title: '商家',
      icon: amapIcon(AMap, '/images/markers/shop.svg', 32, 42),
      offset: new AMap.Pixel(-16, -40)
    }))
  }
  if (props.user) {
    map.add(new AMap.Marker({
      position: [props.user.lon, props.user.lat],
      title: '收货',
      icon: amapIcon(AMap, '/images/markers/user.svg', 32, 42),
      offset: new AMap.Pixel(-16, -40)
    }))
  }
  scheduleInvalidate()
}

async function render() {
  if (!box.value) return
  try {
    const cfg = (await api('/api/map/config')).data || {}
    provider = cfg.provider
    if (provider === 'amap' && cfg.key) await drawAmap(cfg.key)
    else await drawLeaflet()
  } catch {
    await drawLeaflet()
  }
}

onMounted(async () => {
  await render()
  if (box.value && typeof ResizeObserver !== 'undefined') {
    resizeObs = new ResizeObserver(() => scheduleInvalidate())
    resizeObs.observe(box.value)
  }
})
watch(() => [props.points, props.rider, props.merchant, props.user], render, { deep: true })
onUnmounted(() => {
  clearSizeTimers()
  if (resizeObs) resizeObs.disconnect()
  if (map && map.remove) map.remove()
  if (map && map.destroy) map.destroy()
})
</script>

<template>
  <div class="map-wrap">
    <div ref="box" class="map-box" :class="{ tall }"></div>
    <div v-if="eta" class="eta-bubble">{{ eta }}</div>
  </div>
</template>
