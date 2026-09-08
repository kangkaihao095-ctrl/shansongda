import { homePath } from './session'

export function resolveNavigation(to, me) {
  if (to.meta?.public) {
    if (me) return homePath(me.role)
    return true
  }
  if (!me) return '/login'
  if (to.meta?.role && me.role !== to.meta.role) return homePath(me.role)
  return true
}
