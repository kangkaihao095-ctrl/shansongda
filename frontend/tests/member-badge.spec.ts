import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MemberBadge from '../src/components/MemberBadge.vue'

describe('MemberBadge', () => {
  it('uses 闪会员 as the main label', () => {
    const wrapper = mount(MemberBadge, {
      props: { member: { subscribed: true, level: 3, title: '金卡', yearMember: true, expired: false } }
    })
    expect(wrapper.text()).toContain('闪会员·金卡')
    expect(wrapper.text()).toContain('年')
    expect(wrapper.find('.member-shine').exists()).toBe(true)
  })

  it('keeps compact review badges as 闪会员', () => {
    const wrapper = mount(MemberBadge, {
      props: { compact: true, member: { subscribed: true, level: 3, title: '金卡', expired: false } }
    })
    expect(wrapper.text()).toContain('闪会员')
    expect(wrapper.text()).not.toContain('闪会员·金卡')
  })
})
