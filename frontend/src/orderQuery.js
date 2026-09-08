import { computed, onUnmounted, ref, watch } from 'vue'
import { api } from './api'
import { toast } from './session'

export const PAGE_SIZES = [5, 10, 20]
export const DEFAULT_PAGE_SIZE = 10

export function emptyCopy(role, kind = 'list') {
  if (kind === 'hall') return '骑手身份下暂无可接或进行中的订单'
  if (kind === 'done') return '完成后的配送会出现在这里'
  if (kind === 'pending') return '商家身份下暂无待接订单'
  if (kind === 'paid') return '暂无待抢订单'
  if (role === 'RIDER') return '骑手身份下暂无已完成任务'
  if (role === 'MERCHANT') return '商家身份下暂无店铺订单'
  return '还没有外卖订单'
}

export function useOrderQuery(getExtra) {
  const q = ref('')
  const size = ref(DEFAULT_PAGE_SIZE)
  const page = ref(1)
  const items = ref([])
  const total = ref(0)
  const hasNext = ref(false)
  const hasPrev = ref(false)
  const loading = ref(false)
  const loaded = ref(false)
  let debounceTimer

  function extra() {
    return typeof getExtra === 'function' ? (getExtra() || {}) : (getExtra || {})
  }

  async function load() {
    loading.value = true
    try {
      const params = extra()
      const qs = new URLSearchParams({
        size: String(size.value),
        page: String(page.value)
      })
      const keyword = q.value.trim()
      if (keyword) qs.set('q', keyword)
      if (params.status) qs.set('status', params.status)
      if (params.scene) qs.set('scene', params.scene)
      const data = (await api('/api/orders?' + qs.toString())).data || {}
      items.value = data.items || []
      total.value = data.total ?? 0
      hasNext.value = !!data.hasNext
      hasPrev.value = !!data.hasPrev
    } catch (e) {
      toast(e.message, 'err')
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  function setSize(n) {
    size.value = n
    page.value = 1
    return load()
  }

  function setPage(n) {
    page.value = Math.max(1, n)
    return load()
  }

  function prev() {
    if (!hasPrev.value) return Promise.resolve()
    return setPage(page.value - 1)
  }

  function next() {
    if (!hasNext.value) return Promise.resolve()
    return setPage(page.value + 1)
  }

  function resetAndLoad() {
    page.value = 1
    return load()
  }

  watch(q, () => {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      page.value = 1
      load()
    }, 300)
  })

  onUnmounted(() => clearTimeout(debounceTimer))

  const pages = computed(() => Math.max(1, Math.ceil((total.value || 0) / size.value) || 1))
  const empty = computed(() => loaded.value && !loading.value && items.value.length === 0)

  return {
    q, size, page, items, total, pages, hasNext, hasPrev, loading, loaded, empty,
    load, setSize, setPage, prev, next, resetAndLoad
  }
}
