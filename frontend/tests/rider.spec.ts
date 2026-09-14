import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import TabBar from '../src/components/TabBar.vue'
import { session } from '../src/session.js'
import { chartLabelStep, formatRemain, loadAutoReport, saveAutoReport, walkLocation } from '../src/riderReport.js'
import { canGrabNewOrders } from '../src/riderLive.js'

describe('rider report helpers', () => {
  it('persists auto-report flag per user', () => {
    saveAutoReport(2, true)
    expect(loadAutoReport(2, undefined)).toBe(true)
    saveAutoReport(2, false)
    expect(loadAutoReport(2, undefined)).toBe(false)
    expect(loadAutoReport(2, true)).toBe(true)
  })

  it('walks location instead of staying on the same point', () => {
    const a = walkLocation(31.2304, 121.4737, 0.4)
    const b = walkLocation(a.lat, a.lon, a.heading)
    expect(a.lat).not.toBe(31.2304)
    expect(Math.abs(b.lat - a.lat) + Math.abs(b.lon - a.lon)).toBeGreaterThan(0)
  })

  it('thins 30-day chart labels and formats remain clock', () => {
    expect(chartLabelStep(30)).toBeGreaterThan(1)
    expect(formatRemain(3661)).toBe('1:01:01')
  })

  it('blocks new grabs unless rider is online and not force-offline', () => {
    expect(canGrabNewOrders('ONLINE', false)).toBe(true)
    expect(canGrabNewOrders('OFFLINE', false)).toBe(false)
    expect(canGrabNewOrders('ONLINE', true)).toBe(false)
    expect(canGrabNewOrders('OFFLINE', true)).toBe(false)
  })
})

describe('rider tab bar', () => {
  afterEach(() => { session.me = null })

  it('splits hall / tasks / income / me and does not mark hall active on income', async () => {
    session.me = { role: 'RIDER' }
    const Blank = defineComponent({ template: '<div />' })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/rider', component: Blank },
        { path: '/rider/income', component: Blank },
        { path: '/orders', component: Blank },
        { path: '/me', component: Blank }
      ]
    })
    await router.push('/rider/income')
    await router.isReady()
    const wrapper = mount(TabBar, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('大厅')
    expect(wrapper.text()).toContain('任务')
    expect(wrapper.text()).toContain('收入')
    expect(wrapper.text()).toContain('我的')
    const links = wrapper.findAll('a')
    const hall = links.find((a) => a.text().includes('大厅'))
    const income = links.find((a) => a.text().includes('收入'))
    expect(hall?.classes()).not.toContain('active')
    expect(income?.classes()).toContain('active')
  })
})
