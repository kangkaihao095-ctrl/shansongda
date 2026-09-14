import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const login = vi.fn()
const register = vi.fn()
vi.mock('../src/session.js', async () => {
  const actual = await vi.importActual<typeof import('../src/session.js')>('../src/session.js')
  return {
    ...actual,
    login: (...args: unknown[]) => login(...args),
    register: (...args: unknown[]) => register(...args)
  }
})
vi.mock('../src/components/CityNightBg.vue', () => ({
  default: { template: '<div class="login-bg" />' }
}))

import LoginView from '../src/views/LoginView.vue'

const Blank = defineComponent({ template: '<div />' })

async function mountLogin() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginView },
      { path: '/home', component: Blank },
      { path: '/rider', component: Blank },
      { path: '/shop', component: Blank }
    ]
  })
  await router.push('/login')
  await router.isReady()
  return mount(LoginView, { global: { plugins: [router] } })
}

describe('LoginView', () => {
  beforeEach(() => {
    login.mockReset()
    register.mockReset()
  })

  it('shows slogan and prefilled user account without demo cards', async () => {
    const wrapper = await mountLogin()
    expect(wrapper.text()).toContain('闪送达')
    expect(wrapper.text()).toContain('更快达，就闪送达')
    expect(wrapper.text()).toContain('用户')
    expect(wrapper.text()).toContain('骑手')
    expect(wrapper.text()).toContain('商家')
    expect(wrapper.text()).not.toContain('推荐账号')
    expect(wrapper.text()).not.toContain('演示账号')
    expect(wrapper.findAll('.login-card').length).toBe(0)
    const inputs = wrapper.findAll('input')
    expect((inputs[0].element as HTMLInputElement).value).toBe('13800000001')
    expect((inputs[1].element as HTMLInputElement).value).toBe('demo123456')
    expect((inputs[1].element as HTMLInputElement).type).toBe('password')
    expect(wrapper.find('.login-eye').exists()).toBe(true)
  })

  it('switching role only prefills and does not auto login', async () => {
    const wrapper = await mountLogin()
    const pills = wrapper.findAll('.login-seg button')
    await pills[1].trigger('click')
    await flushPromises()
    expect(login).not.toHaveBeenCalled()
    const inputs = wrapper.findAll('input')
    expect((inputs[0].element as HTMLInputElement).value).toBe('13800000002')
    expect((inputs[1].element as HTMLInputElement).value).toBe('demo123456')
    await wrapper.find('.btn').trigger('click')
    await flushPromises()
    expect(login).toHaveBeenCalledTimes(1)
    expect(login).toHaveBeenCalledWith('13800000002', 'demo123456')
  })

  it('toggles to register form and submits role', async () => {
    register.mockResolvedValue({ role: 'USER' })
    const wrapper = await mountLogin()
    const modes = wrapper.findAll('.login-mode button')
    await modes[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('注册并进入')
    const inputs = wrapper.findAll('input')
    expect((inputs[0].element as HTMLInputElement).value).toBe('')
    await inputs[0].setValue('13900001111')
    await inputs[1].setValue('demo123456')
    await wrapper.find('.btn').trigger('click')
    await flushPromises()
    expect(register).toHaveBeenCalledWith('13900001111', 'demo123456', 'USER', '')
    expect(login).not.toHaveBeenCalled()
  })
})
