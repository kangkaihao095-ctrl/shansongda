<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import HeroBackdrop from '../components/HeroBackdrop.vue'
import Icon from '../components/Icon.vue'
import { APP_NAME, SLOGAN } from '../brand'
import { homePath, login, register, toast } from '../session'

const router = useRouter()
const accounts = [
  { role: 'USER', phone: '13800000001', password: 'demo123456', title: '用户', icon: 'bag', tip: '逛店下单 · 秒杀抢券 · 实时轨迹' },
  { role: 'RIDER', phone: '13800000002', password: 'demo123456', title: '骑手', icon: 'bike', tip: '附近订单 · 收入 · 工时提醒' },
  { role: 'MERCHANT', phone: '13800000003', password: 'demo123456', title: '商家', icon: 'store', tip: '自动接单 · 商品改价 · 今日经营' }
]
const highlights = [
  { icon: 'zap', text: '手慢无秒杀' },
  { icon: 'route', text: '送到看得见' },
  { icon: 'activity', text: '自动选好券' }
]

const mode = ref('login')
const role = ref('USER')
const phone = ref(accounts[0].phone)
const password = ref(accounts[0].password)
const nickname = ref('')
const showPwd = ref(false)
const busy = ref(false)

const roleIndex = computed(() => Math.max(0, accounts.findIndex((a) => a.role === role.value)))
const roleTip = computed(() => accounts[roleIndex.value].tip)

function pick(item) {
  role.value = item.role
  if (mode.value !== 'login') return
  phone.value = item.phone
  password.value = item.password
}

function switchMode(next) {
  if (mode.value === next) return
  mode.value = next
  if (next === 'register') {
    phone.value = ''
    password.value = ''
    nickname.value = ''
    role.value = 'USER'
    return
  }
  const hit = accounts.find((a) => a.role === role.value) || accounts[0]
  phone.value = hit.phone
  password.value = hit.password
}

function valid() {
  const mobile = String(phone.value || '').trim()
  if (!/^1\d{10}$/.test(mobile)) {
    toast('请输入 11 位手机号', 'err')
    return false
  }
  if (String(password.value || '').length < 6) {
    toast(mode.value === 'register' ? '密码太短，至少 6 位' : '请输入密码', 'err')
    return false
  }
  return true
}

async function run(fn) {
  if (!valid()) return
  busy.value = true
  try {
    const me = await fn()
    router.replace(homePath(me.role))
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

function submit() {
  const mobile = String(phone.value || '').trim()
  if (mode.value === 'register') {
    return run(() => register(mobile, password.value, role.value, nickname.value))
  }
  return run(() => login(mobile, password.value))
}
</script>

<template>
  <div class="phone page login-phone" data-theme="user">
    <HeroBackdrop />
    <div class="login-scroll">
      <section class="login-hero">
        <div class="login-brand">
          <img src="/logo.svg" alt="" width="44" height="44" />
          <div>
            <h1>{{ APP_NAME }}</h1>
            <p>{{ SLOGAN }}</p>
          </div>
        </div>
        <h2 class="login-headline">同城即时配送<br /><em>快</em>到每一份期待</h2>
        <ul class="login-highlights">
          <li v-for="h in highlights" :key="h.text"><Icon :name="h.icon" :size="14" />{{ h.text }}</li>
        </ul>
      </section>

      <form class="login-box" novalidate @submit.prevent>
        <div class="login-mode" role="tablist" aria-label="登录或注册">
          <i class="login-thumb" :style="{ transform: mode === 'register' ? 'translateX(100%)' : 'none' }" aria-hidden="true"></i>
          <button type="button" role="tab" :aria-selected="mode === 'login'" :class="{ on: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button type="button" role="tab" :aria-selected="mode === 'register'" :class="{ on: mode === 'register' }" @click="switchMode('register')">注册</button>
        </div>

        <div class="login-label">选择身份</div>
        <div class="login-seg" role="radiogroup" aria-label="身份">
          <button
            v-for="a in accounts"
            :key="a.role"
            type="button"
            role="radio"
            :aria-checked="role === a.role"
            :class="[a.role, { on: role === a.role }]"
            @click="pick(a)"
          >
            <Icon :name="a.icon" :size="20" />
            <span>{{ a.title }}</span>
          </button>
        </div>
        <transition name="fade" mode="out-in">
          <p :key="role" class="login-tip">{{ roleTip }}</p>
        </transition>

        <label class="login-field">
          <Icon name="phone" :size="18" />
          <input v-model="phone" placeholder="手机号" inputmode="tel" autocomplete="username" maxlength="11" aria-label="手机号" @keydown.enter="submit" />
        </label>
        <label class="login-field login-pass">
          <Icon name="shield" :size="18" />
          <input
            v-model="password"
            :type="showPwd ? 'text' : 'password'"
            :placeholder="mode === 'register' ? '密码（至少 6 位）' : '密码'"
            :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
            aria-label="密码"
            @keydown.enter="submit"
          />
          <button class="login-eye" type="button" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
            <Icon :name="showPwd ? 'eye' : 'eye-off'" :size="18" />
          </button>
        </label>
        <transition name="fade">
          <label v-if="mode === 'register'" class="login-field">
            <Icon name="user" :size="18" />
            <input v-model="nickname" placeholder="昵称（可选）" maxlength="32" autocomplete="nickname" aria-label="昵称" @keydown.enter="submit" />
          </label>
        </transition>

        <button class="btn btn-lg btn-block login-submit" type="button" :disabled="busy" @click="submit">
          <span v-if="busy" class="spinner"></span>
          <template v-if="mode === 'register'">{{ busy ? '注册中…' : '注册并进入' }}</template>
          <template v-else>{{ busy ? '登录中…' : '登录' }}</template>
          <Icon v-if="!busy" name="chevron" :size="18" :stroke="2.4" />
        </button>
        <button
          v-if="mode === 'login'"
          class="login-reg"
          type="button"
          :disabled="busy"
          @click="switchMode('register')"
        >没有账号？<b>去注册</b></button>
        <button
          v-else
          class="login-reg"
          type="button"
          :disabled="busy"
          @click="switchMode('login')"
        >已有账号？<b>去登录</b></button>
      </form>
      <p class="login-foot">体验账号已预填，切换身份即可体验三端 · 模拟支付密码 147258</p>
    </div>
  </div>
</template>

<style scoped>
.login-phone { background: #0B0907; color: #fff; }
.login-scroll {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  display: flex;
  flex-direction: column;
  padding: calc(28px + env(safe-area-inset-top, 0px)) 20px calc(24px + env(safe-area-inset-bottom, 0px));
}
.login-scroll::-webkit-scrollbar { display: none; }

.login-hero { padding: 8px 4px 0; animation: rise 640ms var(--ease-out) both; }
.login-brand { display: flex; align-items: center; gap: 12px; }
.login-brand img { border-radius: 13px; box-shadow: 0 12px 28px -10px rgba(255, 140, 0, 0.8); }
.login-brand h1 { font-size: 20px; font-weight: 800; letter-spacing: 0.06em; }
.login-brand p { font-size: 12px; font-weight: 700; color: #FFC31F; letter-spacing: 0.1em; margin-top: 1px; }
.login-headline {
  margin-top: clamp(64px, 16vh, 150px);
  font-size: 34px;
  line-height: 1.18;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.login-headline em {
  font-style: normal;
  background: var(--grad-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.login-highlights { list-style: none; margin: 16px 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: 8px; }
.login-highlights li {
  display: inline-flex; align-items: center; gap: 6px;
  height: 28px; padding: 0 12px;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.07);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
  font-size: 12px; font-weight: 600; color: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(8px);
}
.login-highlights .ico { color: #FFC31F; }

.login-box {
  margin-top: 24px;
  padding: 18px 16px 12px;
  border-radius: 28px;
  background: rgba(28, 22, 17, 0.62);
  backdrop-filter: blur(24px) saturate(1.4);
  -webkit-backdrop-filter: blur(24px) saturate(1.4);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.1), 0 30px 60px -20px rgba(0, 0, 0, 0.6);
  animation: rise 720ms 80ms var(--ease-out) both;
}
.login-mode {
  position: relative;
  display: grid; grid-template-columns: 1fr 1fr;
  padding: 4px;
  border-radius: var(--r-pill);
  background: rgba(0, 0, 0, 0.32);
}
.login-thumb {
  position: absolute; top: 4px; bottom: 4px; left: 4px;
  width: calc(50% - 4px);
  border-radius: var(--r-pill);
  background: #fff;
  transition: transform var(--dur-3) var(--ease-out);
}
.login-mode button {
  position: relative; z-index: 1;
  height: 36px;
  border-radius: var(--r-pill);
  color: rgba(255, 255, 255, 0.66);
  font-weight: 700;
  transition: color var(--dur-2) ease;
}
.login-mode button.on { color: #1A1208; }
.login-label { margin: 16px 2px 8px; font-size: 12px; font-weight: 650; color: rgba(255, 255, 255, 0.5); }
.login-seg { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.login-seg button {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
  height: 66px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.62);
  font-size: 13px; font-weight: 700;
  transition: background var(--dur-3) var(--ease-out), color var(--dur-2) ease, box-shadow var(--dur-3) ease, transform var(--dur-1) ease;
}
.login-seg button:active { transform: scale(0.96); }
.login-seg button.on { color: #fff; }
.login-seg button.USER.on { background: linear-gradient(150deg, rgba(255, 195, 31, 0.28), rgba(255, 106, 0, 0.18)); box-shadow: inset 0 0 0 1.5px #FFB300; }
.login-seg button.USER.on .ico { color: #FFC31F; }
.login-seg button.RIDER.on { background: linear-gradient(150deg, rgba(45, 212, 191, 0.26), rgba(14, 165, 160, 0.14)); box-shadow: inset 0 0 0 1.5px #2DD4BF; }
.login-seg button.RIDER.on .ico { color: #5EEAD4; }
.login-seg button.MERCHANT.on { background: linear-gradient(150deg, rgba(34, 176, 125, 0.28), rgba(18, 138, 100, 0.14)); box-shadow: inset 0 0 0 1.5px #34D399; }
.login-seg button.MERCHANT.on .ico { color: #6EE7B7; }
.login-tip { margin: 10px 2px 14px; font-size: 12px; color: rgba(255, 255, 255, 0.5); min-height: 18px; }

.login-field {
  display: flex; align-items: center; gap: 10px;
  height: 52px;
  padding: 0 8px 0 16px;
  margin-bottom: 10px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.5);
  transition: box-shadow var(--dur-2) ease, background var(--dur-2) ease;
}
.login-field:focus-within { background: rgba(255, 255, 255, 0.11); box-shadow: inset 0 0 0 1.5px #FFB300, 0 0 0 4px rgba(255, 179, 0, 0.14); color: #FFC31F; }
.login-field input {
  flex: 1; min-width: 0; height: 100%;
  border: 0; background: transparent;
  color: #fff; font-size: 16px; font-weight: 600; letter-spacing: 0.02em;
}
.login-field input::placeholder { color: rgba(255, 255, 255, 0.4); font-weight: 500; }
.login-eye {
  width: 38px; height: 38px; border-radius: 12px;
  display: grid; place-items: center;
  color: rgba(255, 255, 255, 0.6);
  transition: background var(--dur-2) ease, color var(--dur-2) ease;
}
.login-eye:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.login-submit { margin-top: 6px; }
.login-reg { width: 100%; height: 40px; margin-top: 6px; color: rgba(255, 255, 255, 0.55); font-size: 13px; }
.login-reg b { color: #FFC31F; font-weight: 700; }
.login-foot { margin-top: 16px; text-align: center; font-size: 11px; color: rgba(255, 255, 255, 0.36); line-height: 1.6; }
</style>
