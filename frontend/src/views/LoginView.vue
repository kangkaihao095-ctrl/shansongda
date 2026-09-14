<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import CityNightBg from '../components/CityNightBg.vue'
import { APP_NAME, SLOGAN } from '../brand'
import { homePath, login, register, toast } from '../session'

const router = useRouter()
const accounts = [
  { role: 'USER', phone: '13800000001', password: 'demo123456', title: '用户' },
  { role: 'RIDER', phone: '13800000002', password: 'demo123456', title: '骑手' },
  { role: 'MERCHANT', phone: '13800000003', password: 'demo123456', title: '商家' }
]

const mode = ref('login')
const role = ref('USER')
const phone = ref(accounts[0].phone)
const password = ref(accounts[0].password)
const nickname = ref('')
const showPwd = ref(false)
const busy = ref(false)

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
  <div class="phone page login-phone">
    <CityNightBg />
    <section class="login-panel">
      <div class="login-box">
        <div class="login-brand">
          <img src="/logo.svg" alt="闪送达" width="48" height="48" />
          <h1>{{ APP_NAME }}</h1>
          <p>{{ SLOGAN }}</p>
        </div>
        <div class="login-mode" role="tablist">
          <button type="button" role="tab" :class="{ on: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button type="button" role="tab" :class="{ on: mode === 'register' }" @click="switchMode('register')">注册</button>
        </div>
        <div class="login-seg" role="tablist">
          <button
            v-for="a in accounts"
            :key="a.role"
            type="button"
            role="tab"
            :class="[a.role, { on: role === a.role }]"
            @click="pick(a)"
          >{{ a.title }}</button>
        </div>
        <input class="field" v-model="phone" placeholder="手机号" inputmode="tel" autocomplete="username" />
        <div class="login-pass">
          <input
            class="field"
            v-model="password"
            :type="showPwd ? 'text' : 'password'"
            :placeholder="mode === 'register' ? '密码（至少 6 位）' : '密码'"
            :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
          />
          <button class="login-eye" type="button" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
            <svg v-if="showPwd" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path fill="none" stroke="currentColor" stroke-width="1.8" d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/>
              <circle cx="12" cy="12" r="2.4" fill="none" stroke="currentColor" stroke-width="1.8"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path fill="none" stroke="currentColor" stroke-width="1.8" d="M3 3l18 18M9.5 9.6A3 3 0 0 0 12 15a3 3 0 0 0 2.7-1.7"/>
              <path fill="none" stroke="currentColor" stroke-width="1.8" d="M4 12s2.8-5 8-5c1.3 0 2.5.3 3.5.8M20 12s-1.2 2.1-3.2 3.6"/>
            </svg>
          </button>
        </div>
        <input
          v-if="mode === 'register'"
          class="field"
          v-model="nickname"
          placeholder="昵称（可选）"
          maxlength="32"
          autocomplete="nickname"
        />
        <button class="btn" style="width:100%;margin-top:4px" :disabled="busy" @click="submit">
          <template v-if="mode === 'register'">{{ busy ? '注册中…' : '注册并进入' }}</template>
          <template v-else>{{ busy ? '登录中…' : '登录' }}</template>
        </button>
        <button
          v-if="mode === 'login'"
          class="login-reg"
          type="button"
          :disabled="busy"
          @click="switchMode('register')"
        >没有账号？去注册</button>
        <button
          v-else
          class="login-reg"
          type="button"
          :disabled="busy"
          @click="switchMode('login')"
        >已有账号？去登录</button>
      </div>
    </section>
  </div>
</template>
