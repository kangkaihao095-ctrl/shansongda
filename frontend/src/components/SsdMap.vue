<script setup>
import { computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { api } from '../api'

const props = defineProps({
  points: { type: Array, default: () => [] },
  segments: { type: Array, default: () => [] },
  rider: Object,
  riders: { type: Array, default: () => [] },
  merchant: Object,
  user: Object,
  eta: String,
  hint: { type: String, default: '' },
  tall: Boolean,
  compact: Boolean,
  clusters: { type: Array, default: () => [] }
})

const AMAP_WEBRD = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'
const AMAP_VEC = 'https://wprd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&style=7&x={x}&y={y}&z={z}&scl=1&ltype=0'
const OSM = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'

const box = { value: null }
let map
let usingAmap = false
let resizeObs
let sizeTimers = []
let pathLayers = []
let clusterLayers = []
let riderLayers = []
const markers = { rider: null, merchant: null, user: null }
let interacting = 0
let interactTimer
let lastFitKey = ''
let bootPromise

const GREEN = '#2BA471'
const YELLOW = '#E6A817'
const RED = '#D14B3A'

function congestionColor(value) {
  const n = Number(value)
  if (n > 1.6) return RED
  if (n > 1.3) return YELLOW
  return GREEN
}

function routeSegments() {
  const segs = (props.segments || []).filter((s) => s?.from?.lat != null && s?.to?.lat != null)
  if (segs.length) return segs
  const pts = props.points || []
  const fallback = []
  for (let i = 0; i < pts.length - 1; i++) {
    if (pts[i]?.lat == null || pts[i + 1]?.lat == null) continue
    fallback.push({ from: pts[i], to: pts[i + 1], congestion: pts[i].congestion ?? 1 })
  }
  return fallback
}

const hasPath = computed(() => routeSegments().length > 0 || (props.points || []).length > 1)
const trafficHint = computed(() => {
  if (!hasPath.value) return ''
  return props.hint || ''
})

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

function coordKey(p) {
  if (p?.lat == null || p?.lon == null) return ''
  return `${Number(p.lat).toFixed(5)},${Number(p.lon).toFixed(5)}`
}

function pathKey() {
  const segs = routeSegments()
  if (segs.length) {
    return segs.map((s) => `${coordKey(s.from)}>${coordKey(s.to)}:${s.congestion ?? ''}`).join('|')
  }
  return (props.points || []).map((p) => coordKey(p)).join('|')
}

function fitKey() {
  return [coordKey(props.merchant), coordKey(props.user), pathKey(), String(riderList().length)].join('#')
}

function riderList() {
  const list = [...(props.riders || [])]
  if (props.rider && Number.isFinite(props.rider.lat) && Number.isFinite(props.rider.lon)) {
    const key = coordKey(props.rider)
    const id = props.rider.riderId ?? props.rider.userId
    const dup = list.some((r) => (id != null && (r.riderId ?? r.userId) === id) || coordKey(r) === key)
    if (!dup) list.unshift(props.rider)
  }
  return list.filter((r) => Number.isFinite(r?.lat) && Number.isFinite(r?.lon))
}

function ridersKey() {
  return riderList().map((r) => `${r.riderId ?? r.userId ?? ''}:${coordKey(r)}:${r.onlineStatus || ''}`).join('|')
}

function overlaySnapshot() {
  return {
    r: coordKey(props.rider),
    rs: ridersKey(),
    m: coordKey(props.merchant),
    u: coordKey(props.user),
    p: pathKey(),
    c: (props.clusters || []).map((x) => `${x.lat},${x.lon},${x.count}`).join('|')
  }
}

function allPoints() {
  const pts = []
  riderList().forEach((r) => pts.push([r.lat, r.lon]))
  routeSegments().forEach((s) => {
    pts.push([s.from.lat, s.from.lon], [s.to.lat, s.to.lon])
  })
  ;(props.points || []).forEach((p) => pts.push([p.lat, p.lon]))
  if (props.merchant) pts.push([props.merchant.lat, props.merchant.lon])
  if (props.user) pts.push([props.user.lat, props.user.lon])
  return pts.filter((p) => Number.isFinite(p[0]) && Number.isFinite(p[1]))
}

function pathPoints() {
  const segs = routeSegments()
  if (segs.length) {
    return segs.flatMap((s) => [[s.from.lat, s.from.lon], [s.to.lat, s.to.lon]])
  }
  return (props.points || []).filter((p) => Number.isFinite(p?.lat) && Number.isFinite(p?.lon)).map((p) => [p.lat, p.lon])
}

function riderTitle(r) {
  const name = r.displayName || '骑手'
  return r.onlineStatus === 'OFFLINE' ? `${name} · 休息中` : `${name} · 在线`
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

function noteInteractStart() {
  interacting += 1
  if (interactTimer) clearTimeout(interactTimer)
}

function noteInteractEnd() {
  if (interactTimer) clearTimeout(interactTimer)
  interactTimer = setTimeout(() => { interacting = 0 }, 480)
}

function canFit() {
  return interacting <= 0
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

function bindLeafletInteract(target) {
  target.on('dragstart', noteInteractStart)
  target.on('zoomstart', noteInteractStart)
  target.on('dragend', noteInteractEnd)
  target.on('zoomend', noteInteractEnd)
}

function bindAmapInteract(target) {
  target.on('dragstart', noteInteractStart)
  target.on('zoomstart', noteInteractStart)
  target.on('dragend', noteInteractEnd)
  target.on('zoomend', noteInteractEnd)
}

function clearLeafletPaths() {
  pathLayers.forEach((l) => map.removeLayer(l))
  pathLayers = []
}

function syncLeafletMarker(kind, point, icon, title) {
  const L = window.L
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) {
    if (markers[kind]) {
      map.removeLayer(markers[kind])
      markers[kind] = null
    }
    return
  }
  const latlng = [point.lat, point.lon]
  if (markers[kind]) {
    markers[kind].setLatLng(latlng)
    return
  }
  markers[kind] = L.marker(latlng, { icon }).addTo(map).bindTooltip(title)
}

function syncLeafletClusters(L) {
  clusterLayers.forEach((layer) => map.removeLayer(layer))
  clusterLayers = []
  for (const c of props.clusters || []) {
    if (c?.lat == null || c?.lon == null) continue
    const r = 10 + Math.min(18, (Number(c.count) || 1) * 3)
    const layer = L.circleMarker([c.lat, c.lon], {
      radius: r,
      color: '#C43D2F',
      fillColor: '#C43D2F',
      fillOpacity: 0.28,
      weight: 1
    }).bindTooltip((c.count || 1) + ' 单')
    layer.addTo(map)
    clusterLayers.push(layer)
  }
}

function syncLeafletRiders(L) {
  riderLayers.forEach((layer) => map.removeLayer(layer))
  riderLayers = []
  if (markers.rider) {
    map.removeLayer(markers.rider)
    markers.rider = null
  }
  for (const r of riderList()) {
    const off = r.onlineStatus === 'OFFLINE'
    const icon = L.divIcon({
      className: 'ssd-rider-pin' + (off ? ' off' : ''),
      iconSize: [40, 44],
      iconAnchor: [20, 40],
      html: '<img src="/images/markers/rider.svg" alt="" />'
    })
    const layer = L.marker([r.lat, r.lon], { icon, zIndexOffset: off ? 0 : 80 }).addTo(map).bindTooltip(riderTitle(r))
    riderLayers.push(layer)
  }
}

function fitLeaflet(shouldFit, pathPts) {
  if (!shouldFit) return
  const pts = pathPts.length > 1 ? pathPts : allPoints()
  if (pts.length > 1) map.fitBounds(pts, { padding: [28, 28] })
  else if (pts.length === 1) map.setView(pts[0], 15)
}

function syncLeaflet(shouldFit) {
  const L = window.L
  const segs = routeSegments()
  const pts = pathPoints()
  if (segs.length) {
    if (pathLayers.length === segs.length) {
      segs.forEach((s, i) => {
        pathLayers[i].setLatLngs([[s.from.lat, s.from.lon], [s.to.lat, s.to.lon]])
        pathLayers[i].setStyle({ color: congestionColor(s.congestion), weight: 6, opacity: 0.92 })
      })
    } else {
      clearLeafletPaths()
      pathLayers = segs.map((s) => L.polyline(
        [[s.from.lat, s.from.lon], [s.to.lat, s.to.lon]],
        { color: congestionColor(s.congestion), weight: 6, opacity: 0.92 }
      ).addTo(map))
    }
    fitLeaflet(shouldFit, pts)
  } else if (pts.length > 1) {
    if (pathLayers.length === 1) pathLayers[0].setLatLngs(pts)
    else {
      clearLeafletPaths()
      pathLayers = [L.polyline(pts, { color: GREEN, weight: 5, opacity: 0.9 }).addTo(map)]
    }
    fitLeaflet(shouldFit, pts)
  } else {
    clearLeafletPaths()
    fitLeaflet(shouldFit, [])
  }
  syncLeafletRiders(L)
  syncLeafletMarker('merchant', props.merchant, pin(L, '/images/markers/shop.svg', [32, 42], [16, 40]), '商家')
  syncLeafletMarker('user', props.user, pin(L, '/images/markers/user.svg', [32, 42], [16, 40]), '收货')
  syncLeafletClusters(L)
}

function clearAmapPaths() {
  if (pathLayers.length) map.remove(pathLayers)
  pathLayers = []
}

function clearAmapRiders() {
  if (riderLayers.length) map.remove(riderLayers)
  riderLayers = []
  if (markers.rider) {
    map.remove(markers.rider)
    markers.rider = null
  }
}

function syncAmapRiders() {
  const AMap = window.AMap
  clearAmapRiders()
  riderLayers = riderList().map((r) => {
    const off = r.onlineStatus === 'OFFLINE'
    const marker = new AMap.Marker({
      position: [r.lon, r.lat],
      title: riderTitle(r),
      icon: amapIcon(AMap, '/images/markers/rider.svg', 40, 44),
      offset: new AMap.Pixel(-20, -40),
      opacity: off ? 0.55 : 1,
      zIndex: off ? 100 : 120
    })
    return marker
  })
  if (riderLayers.length) map.add(riderLayers)
}

function syncAmapMarker(kind, point, icon, title, offset) {
  const AMap = window.AMap
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) {
    if (markers[kind]) {
      map.remove(markers[kind])
      markers[kind] = null
    }
    return
  }
  const pos = [point.lon, point.lat]
  if (markers[kind]) {
    markers[kind].setPosition(pos)
    return
  }
  markers[kind] = new AMap.Marker({ position: pos, title, icon, offset })
  map.add(markers[kind])
}

function syncAmap(shouldFit) {
  const AMap = window.AMap
  const segs = routeSegments()
  const pts = pathPoints()
  if (segs.length) {
    if (pathLayers.length === segs.length) {
      segs.forEach((s, i) => {
        pathLayers[i].setPath([[s.from.lon, s.from.lat], [s.to.lon, s.to.lat]])
        pathLayers[i].setOptions({ strokeColor: congestionColor(s.congestion) })
      })
    } else {
      clearAmapPaths()
      pathLayers = segs.map((s) => new AMap.Polyline({
        path: [[s.from.lon, s.from.lat], [s.to.lon, s.to.lat]],
        strokeColor: congestionColor(s.congestion),
        strokeWeight: 6,
        strokeOpacity: 0.92
      }))
      map.add(pathLayers)
    }
  } else if (pts.length > 1) {
    const path = pts.map((p) => [p[1], p[0]])
    if (pathLayers.length === 1) pathLayers[0].setPath(path)
    else {
      clearAmapPaths()
      pathLayers = [new AMap.Polyline({ path, strokeColor: GREEN, strokeWeight: 6 })]
      map.add(pathLayers)
    }
  } else {
    clearAmapPaths()
  }
  syncAmapRiders()
  syncAmapMarker('merchant', props.merchant, amapIcon(AMap, '/images/markers/shop.svg', 32, 42), '商家', new AMap.Pixel(-16, -40))
  syncAmapMarker('user', props.user, amapIcon(AMap, '/images/markers/user.svg', 32, 42), '收货', new AMap.Pixel(-16, -40))
  clusterLayers.forEach((layer) => map.remove(layer))
  clusterLayers = (props.clusters || []).filter((c) => c?.lat != null).map((c) => new AMap.Circle({
    center: [c.lon, c.lat],
    radius: 80 + Math.min(220, (Number(c.count) || 1) * 40),
    strokeColor: '#C43D2F',
    fillColor: '#C43D2F',
    fillOpacity: 0.22,
    strokeWeight: 1
  }))
  if (clusterLayers.length) map.add(clusterLayers)
  if (shouldFit) {
    const overlay = [...riderLayers, ...pathLayers, markers.merchant, markers.user].filter(Boolean)
    if (overlay.length) map.setFitView(overlay)
  }
}

function syncOverlays(routeChanged) {
  if (!map) return
  const key = fitKey()
  const shouldFit = !!routeChanged && canFit() && key !== lastFitKey
  if (usingAmap) syncAmap(shouldFit)
  else syncLeaflet(shouldFit)
  if (shouldFit || !lastFitKey) lastFitKey = key
}

async function drawLeaflet() {
  loadCss('https://unpkg.com/leaflet@1.9.4/dist/leaflet.css')
  await loadScript('https://unpkg.com/leaflet@1.9.4/dist/leaflet.js')
  const L = window.L
  const pts = allPoints()
  const center = pts[0] || [31.2304, 121.4737]
  map = L.map(box.value, { zoomControl: false }).setView(center, 15)
  addAmapTiles(L, map)
  bindLeafletInteract(map)
  usingAmap = false
}

async function drawAmap(key) {
  await loadScript(`https://webapi.amap.com/maps?v=2.0&key=${key}`)
  const AMap = window.AMap
  const pts = allPoints()
  const center = pts[0] || [31.2304, 121.4737]
  map = new AMap.Map(box.value, { zoom: 15, center: [center[1], center[0]], mapStyle: 'amap://styles/normal' })
  bindAmapInteract(map)
  usingAmap = true
}

async function boot() {
  if (!box.value || map) return
  try {
    const cfg = (await api('/api/map/config')).data || {}
    if (cfg.provider === 'amap' && cfg.key) await drawAmap(cfg.key)
    else await drawLeaflet()
  } catch {
    await drawLeaflet()
  }
  syncOverlays(true)
  scheduleInvalidate()
}

function setBox(el) {
  box.value = el
}

onMounted(async () => {
  bootPromise = boot()
  await bootPromise
  if (box.value && typeof ResizeObserver !== 'undefined') {
    resizeObs = new ResizeObserver(() => scheduleInvalidate())
    resizeObs.observe(box.value)
  }
})

watch(overlaySnapshot, (next, prev) => {
  if (!map) return
  const ridersBoot = !prev || (prev.rs === '' && next.rs !== '')
  const routeChanged = !prev || next.p !== prev.p || next.m !== prev.m || next.u !== prev.u || ridersBoot
  syncOverlays(routeChanged)
})

onUnmounted(() => {
  clearSizeTimers()
  if (interactTimer) clearTimeout(interactTimer)
  if (resizeObs) resizeObs.disconnect()
  if (map && map.remove) map.remove()
  if (map && map.destroy) map.destroy()
  map = null
  pathLayers = []
  riderLayers = []
  markers.rider = markers.merchant = markers.user = null
})
</script>

<template>
  <div class="map-wrap" :class="{ compact }">
    <div :ref="setBox" class="map-box" :class="{ tall, compact }"></div>
    <div v-if="eta" class="eta-bubble">
      {{ eta }}
      <div v-if="trafficHint" class="eta-sub">{{ trafficHint }}</div>
    </div>
    <div v-if="hasPath" class="traffic-legend">
      <b>路况</b>
      <span><i class="dot ok"></i>畅通</span>
      <span><i class="dot slow"></i>缓行</span>
      <span><i class="dot jam"></i>拥堵</span>
    </div>
  </div>
</template>
