const reduceMotion = typeof window !== 'undefined'
  && typeof window.matchMedia === 'function'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const observers = new Map()

function scrollParent(el) {
  let node = el.parentElement
  while (node && node !== document.body && node !== document.documentElement) {
    const oy = getComputedStyle(node).overflowY
    if (oy === 'auto' || oy === 'scroll' || oy === 'overlay') return node
    node = node.parentElement
  }
  return null
}

function inView(el, root) {
  const box = el.getBoundingClientRect()
  const frame = root
    ? root.getBoundingClientRect()
    : { top: 0, bottom: window.innerHeight, left: 0, right: window.innerWidth }
  const visible = Math.min(box.bottom, frame.bottom) - Math.max(box.top, frame.top)
  return visible > 12 && box.width > 0
}

function getObserver(root) {
  const key = root || null
  if (observers.has(key)) return observers.get(key)
  const io = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      revealNow(entry.target)
      io.unobserve(entry.target)
    }
  }, { root, rootMargin: '64px 0px', threshold: 0.01 })
  observers.set(key, io)
  return io
}

function revealNow(el) {
  el.classList.add('is-in')
  if (el._revealOff) {
    el._revealOff()
    el._revealOff = null
  }
}

/**
 * v-reveal：元素进入所在滚动容器时淡入。
 * 绑定值为数字时作为延迟毫秒（列表错峰），最多 360ms。
 * 观察根用最近的 overflow 滚动容器，避免画框内部滚动时一直停在透明态。
 */
export const reveal = {
  mounted(el, binding) {
    if (reduceMotion) return
    const delay = Math.min(360, Number(binding.value) || 0)
    if (delay) el.style.setProperty('--reveal-delay', delay + 'ms')
    el.classList.add('reveal')
    const root = scrollParent(el)
    const io = getObserver(root)
    if (!io) {
      revealNow(el)
      return
    }
    io.observe(el)
    const tick = () => { if (inView(el, root)) revealNow(el) }
    const target = root || window
    target.addEventListener('scroll', tick, { passive: true })
    el._revealOff = () => {
      target.removeEventListener('scroll', tick)
      io.unobserve(el)
    }
    requestAnimationFrame(tick)
  },
  unmounted(el) {
    if (el._revealOff) el._revealOff()
  }
}
