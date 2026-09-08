import { reactive } from 'vue'
import { api, setToken, token } from './api'

export const session = reactive({
  me: null,
  toast: '',
  toastKind: 'ok',
  pendingLoginGift: null
})

export function toast(message, kind = 'ok') {
  session.toast = message
  session.toastKind = kind
  setTimeout(() => {
    if (session.toast === message) session.toast = ''
  }, 2800)
}

export async function loadMe() {
  const res = await api('/api/me')
  session.me = res.data
  return res.data
}

export async function login(phone, password) {
  const res = await api('/api/auth/login', { method: 'POST', body: { phone, password } })
  setToken(res.data.token)
  const me = await loadMe()
  session.pendingLoginGift = res.data.loginGift || null
  if (res.data.grantMessage) toast(res.data.grantMessage)
  return me
}

export async function register(phone, password, role) {
  const res = await api('/api/auth/register', { method: 'POST', body: { phone, password, role } })
  setToken(res.data.token)
  const me = await loadMe()
  if (res.data.grants?.granted?.length) {
    session.pendingLoginGift = { title: '新客礼', subtitle: '新客 15 元无门槛已入账', items: res.data.grants.granted, claimed: true }
  }
  if (res.data.grantMessage) toast(res.data.grantMessage)
  return me
}

export function logout() {
  setToken(null)
  session.me = null
  session.pendingLoginGift = null
  try { sessionStorage.removeItem('ssd.loginGiftShown') } catch { /* ignore */ }
}

export async function boot() {
  if (!token()) return null
  try {
    return await loadMe()
  } catch {
    logout()
    return null
  }
}

export function homePath(role) {
  if (role === 'RIDER') return '/rider'
  if (role === 'MERCHANT') return '/shop'
  return '/home'
}

export function goBack(router, fallback) {
  if (window.history.state?.back != null) {
    router.back()
    return
  }
  const role = session.me?.role
  router.replace(fallback || (role === 'RIDER' ? '/rider' : role === 'MERCHANT' ? '/shop' : '/orders'))
}
