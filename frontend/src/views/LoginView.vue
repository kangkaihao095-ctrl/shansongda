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

const role = ref('USER')
const phone = ref(accounts[0].phone)
const password = ref(accounts[0].password)
const showPwd = ref(true)
const busy = ref(false)

function pick(item) {
  role.value = item.role
  phone.value = item.phone
  password.value = item.password
}

async function run(fn) {
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
</script>

<template>
  <div class="phone page login-phone">
    <CityNightBg />
    <section class="login-panel">
      <div class="login-box">
        <div class="login-brand">
          <img src="/logo.svg" alt="闪送达" width="44" height="44" />
          <h1>{{ APP_NAME }}</h1>
          <p>{{ SLOGAN }}</p>
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
            placeholder="密码"
            autocomplete="current-password"
          />
          <button class="login-eye" type="button" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
            {{ showPwd ? '隐藏' : '显示' }}
          </button>
        </div>
        <button class="btn" style="width:100%;margin-top:4px" :disabled="busy" @click="run(() => login(phone, password))">
          {{ busy ? '登录中…' : '登录' }}
        </button>
        <button class="login-reg" type="button" :disabled="busy" @click="run(() => register(phone, password, role))">
          没有账号？注册并进入
        </button>
      </div>
    </section>
  </div>
</template>
