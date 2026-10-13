<script setup>
import { computed } from 'vue'
import Icon from '../Icon.vue'
import { APP_NAME, SLOGAN } from '../../brand'
import { session } from '../../session'

const FEATURES = {
  GUEST: {
    kicker: '同城即时配送 · 三端一体',
    title: '一份外卖送到家，\n每一步都有人接着',
    desc: '点餐、出餐、取餐到送达，用户、商家和骑手一起把这一单走完。',
    items: [
      { icon: 'bag', title: '用户端', desc: '逛店下单 · 秒杀用券 · 看骑手到哪' },
      { icon: 'bike', title: '骑手端', desc: '附近订单 · 送餐收入 · 工时提醒' },
      { icon: 'store', title: '商家端', desc: '自动接单 · 商品改价 · 今日经营' }
    ]
  },
  USER: {
    kicker: '用户端 · 你正在体验',
    title: '下单那一刻起，\n每一步都看得见',
    desc: '从秒杀抢购到骑手送达，人到哪、还要多久，手机上都能看见。',
    items: [
      { icon: 'flame', title: '限时秒杀', desc: '手慢无，卖完即止，重复点也不会多扣' },
      { icon: 'ticket', title: '智能用券', desc: '结算时自动帮你选最划算的券' },
      { icon: 'route', title: '实时轨迹', desc: '看骑手到哪了，还要多久送到' },
      { icon: 'crown', title: '闪会员', desc: '等级越高运费越优惠，消费还能继续升级' }
    ]
  },
  RIDER: {
    kicker: '骑手端 · 你正在体验',
    title: '抢单要快，\n路线要准',
    desc: '附近新单抢得到，路线看得清，干满工时会提醒你休息。',
    items: [
      { icon: 'zap', title: '附近抢单', desc: '一单只会被一人抢走，抢到就进你的任务' },
      { icon: 'route', title: '送餐路线', desc: '先到店取餐，再送到用户，堵不堵一眼能看清' },
      { icon: 'timer', title: '工时提醒', desc: '每天最多在线 8 小时，到点自动休息，次日重新计算' },
      { icon: 'target', title: '附近订单', desc: '只看你周围能接的单，远单不会挤进大厅' }
    ]
  },
  MERCHANT: {
    kicker: '商家端 · 你正在体验',
    title: '今天卖了多少，\n打开就能看清',
    desc: '超时没接会自动接单，改价马上生效，今日经营一目了然。',
    items: [
      { icon: 'timer', title: '自动接单', desc: '15 分钟没处理：营业中自动接单，打烊则自动退款' },
      { icon: 'chart', title: '今日经营', desc: '营收、订单和热销一眼看清，也能导出报表' },
      { icon: 'edit', title: '商品管理', desc: '上下架、改价、改库存，用户马上就能看到' },
      { icon: 'nav', title: '一键派单', desc: '按附近骑手自动派单，也可以让骑手自己抢' }
    ]
  }
}

const STACK = ['限时秒杀', '自动用券', '实时轨迹', '会员运费', '附近抢单', '送餐路线', '自动接单', '今日经营']

const role = computed(() => session.me?.role || 'GUEST')
const info = computed(() => FEATURES[role.value] || FEATURES.GUEST)
</script>

<template>
  <aside aria-label="项目特色">
    <div class="stage-brand">
      <img src="/logo.svg" alt="" width="40" height="40" />
      <div>
        <b>{{ APP_NAME }}</b>
        <span>{{ SLOGAN }}</span>
      </div>
    </div>
    <transition name="fade" mode="out-in">
      <div :key="role" class="stage-copy">
        <div class="stage-kicker"><i></i>{{ info.kicker }}</div>
        <h1 class="stage-title">{{ info.title }}</h1>
        <p class="stage-desc">{{ info.desc }}</p>
        <ul class="stage-features">
          <li v-for="(f, i) in info.items" :key="f.title" :style="{ '--d': i * 70 + 'ms' }">
            <span class="sf-ico"><Icon :name="f.icon" :size="18" /></span>
            <div>
              <b>{{ f.title }}</b>
              <p>{{ f.desc }}</p>
            </div>
          </li>
        </ul>
      </div>
    </transition>
    <div class="stage-stack">
      <span v-for="s in STACK" :key="s">{{ s }}</span>
    </div>
  </aside>
</template>
