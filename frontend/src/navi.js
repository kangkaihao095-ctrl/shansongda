export function openNavi(lat, lon, name) {
  if (lat == null || lon == null) return
  const ua = navigator.userAgent || ''
  const ios = /iPhone|iPad|iPod/i.test(ua)
  const label = encodeURIComponent(name || '目的地')
  const app = ios
    ? `iosamap://navi?sourceApplication=闪送达&lat=${lat}&lon=${lon}&dev=0&style=2`
    : `amapuri://route/plan/?dlat=${lat}&dlon=${lon}&dname=${label}&dev=0&t=0`
  const web = ios
    ? `https://maps.apple.com/?daddr=${lat},${lon}&dirflg=d`
    : `https://uri.amap.com/navigation?to=${lon},${lat},${label}&mode=car&src=ssd`
  window.location.href = app
  setTimeout(() => {
    if (document.hidden) return
    window.open(web, '_blank')
  }, 800)
}
