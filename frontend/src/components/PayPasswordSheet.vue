<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import Icon from './Icon.vue'
import PinInput from './PinInput.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  channel: { type: String, default: 'WECHAT' },
  amount: { type: String, default: '' },
  merchant: { type: String, default: '' },
  hint: { type: String, default: '' },
  submit: { type: Function, required: true }
})
const emit = defineEmits(['close', 'change-method', 'success'])

const panel = ref(null)
const password = ref('')
const phase = ref('entry')
const errorText = ref('')
const aliShake = ref(false)
const aliBar = ref(false)
const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'blank', '0', 'del']
let seq = 0
let timers = []

const isAli = computed(() => props.channel === 'ALIPAY')
const locked = computed(() => phase.value === 'paying' || phase.value === 'success' || phase.value === 'wx-fail')
const showAnim = computed(() => phase.value === 'paying' || phase.value === 'success')
const pinSlotClass = computed(() => {
  if (phase.value === 'paying' || phase.value === 'success') return isAli.value ? 'is-ali-out' : 'is-wx-collapse'
  if (phase.value === 'wx-fail') return 'is-wx-back'
  return ''
})
const live = computed(() => {
  if (phase.value === 'paying') return isAli.value ? '正在付款' : '支付中'
  if (phase.value === 'success') return isAli.value ? '付款成功' : '支付成功'
  if (errorText.value) return errorText.value
  return `已输入 ${password.value.length} 位支付密码`
})

function reduced() {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function sleep(ms) {
  return new Promise((resolve) => {
    const item = { resolve, t: 0 }
    item.t = setTimeout(resolve, ms)
    timers.push(item)
  })
}

function clearTimers() {
  for (const item of timers) {
    clearTimeout(item.t)
    item.resolve()
  }
  timers = []
}

function reset() {
  seq++
  clearTimers()
  password.value = ''
  phase.value = 'entry'
  errorText.value = ''
  aliShake.value = false
  aliBar.value = false
}

function onKey(e) {
  if (!props.open) return
  if (e.key === 'Escape') {
    e.preventDefault()
    requestClose()
    return
  }
  if (e.key === 'Backspace') {
    e.preventDefault()
    del()
    return
  }
  if (/^\d$/.test(e.key)) {
    e.preventDefault()
    push(e.key)
  }
}

watch(() => props.open, async (v) => {
  if (v) {
    reset()
    await nextTick()
    panel.value?.focus()
    window.addEventListener('keydown', onKey)
  } else {
    seq++
    clearTimers()
    window.removeEventListener('keydown', onKey)
  }
})

onUnmounted(() => {
  seq++
  clearTimers()
  window.removeEventListener('keydown', onKey)
})

function requestClose() {
  if (locked.value) return
  emit('close')
}

function changeMethod() {
  if (locked.value) return
  emit('change-method')
}

function push(digit) {
  if (locked.value || password.value.length >= 6) return
  if (errorText.value) {
    errorText.value = ''
    aliBar.value = false
  }
  password.value += digit
  if (password.value.length === 6) startPay()
}

function del() {
  if (locked.value || !password.value) return
  password.value = password.value.slice(0, -1)
}

async function startPay() {
  if (phase.value === 'paying' || phase.value === 'success') return
  const id = ++seq
  const pin = password.value
  phase.value = 'paying'
  const started = performance.now()
  let outcome = { ok: true }
  try {
    await props.submit(pin)
  } catch (err) {
    const message = err?.message || '支付未完成，请重试'
    const code = err?.payload?.code
    outcome = { ok: false, bad: code === 'BAD_PASSWORD' || /密码/.test(message), message }
  }
  if (id !== seq || !props.open) return
  const wait = (reduced() ? 280 : 1480) - (performance.now() - started)
  if (wait > 0) await sleep(wait)
  if (id !== seq || !props.open) return
  if (outcome.ok) {
    phase.value = 'success'
    await sleep(reduced() ? 640 : 1560)
    if (id !== seq || !props.open) return
    emit('success')
    return
  }
  await showFail(id, outcome)
}

async function showFail(id, outcome) {
  const msg = outcome.bad
    ? (isAli.value ? '支付密码不正确，请重新输入' : '密码不正确，请重试')
    : (outcome.message || '支付未完成，请重试')
  if (isAli.value) {
    password.value = ''
    phase.value = 'entry'
    errorText.value = msg
    aliBar.value = true
    aliShake.value = true
    await sleep(reduced() ? 180 : 560)
    if (id !== seq) return
    aliShake.value = false
    return
  }
  errorText.value = msg
  phase.value = 'wx-fail'
  await sleep(reduced() ? 180 : 520)
  if (id !== seq || !props.open) return
  password.value = ''
  phase.value = 'entry'
}
</script>

<template>
  <Teleport to="#device-portal" defer>
    <transition name="sheet">
      <div v-if="open" class="sheet-mask" @click.self="requestClose">
        <div
          ref="panel"
          class="sheet pay-panel"
          :class="[isAli ? 'alipay' : 'wechat', { 'is-busy': showAnim }]"
          role="dialog"
          aria-modal="true"
          aria-labelledby="pay-sheet-title"
          tabindex="-1"
          :data-phase="phase"
          :data-channel="isAli ? 'alipay' : 'wechat'"
          :aria-busy="phase === 'paying'"
        >
          <div class="pay-inner" :class="{ 'is-ali-shake': aliShake }">
            <div class="sheet-handle" aria-hidden="true"></div>
            <div class="pay-top">
              <button class="pay-back" type="button" :disabled="locked" @click="changeMethod">
                <Icon name="back" :size="16" />
                <span>更换方式</span>
              </button>
              <div class="pay-brand">
                <img :src="isAli ? '/images/pay-alipay.svg' : '/images/pay-wechat.svg'" alt="" />
                <b id="pay-sheet-title">{{ isAli ? '支付宝' : '微信支付' }}</b>
              </div>
              <button class="icon-btn sm plain pay-close" type="button" aria-label="关闭" :disabled="locked" @click="requestClose">
                <Icon name="close" :size="18" />
              </button>
            </div>

            <div class="pay-summary">
              <div class="pay-amt num">{{ amount }}</div>
              <div class="pay-merchant">{{ merchant || '闪送达' }}</div>
              <p v-if="phase === 'entry' || phase === 'wx-fail'" class="pay-ask">请输入 6 位支付密码</p>
            </div>

            <div class="pay-mid">
              <div class="pin-slot" :class="pinSlotClass">
                <PinInput
                  :model-value="password"
                  :tone="isAli ? 'alipay' : 'wechat'"
                  :shake="phase === 'wx-fail'"
                  label="支付密码"
                />
                <p v-if="!isAli && errorText && phase !== 'paying'" class="wx-err">{{ errorText }}</p>
                <p v-else-if="phase === 'entry' && hint && !errorText" class="pay-hint">
                  <Icon name="info" :size="13" />{{ hint }}
                </p>
              </div>

              <div v-if="phase === 'paying' && !isAli" class="wx-load">
                <span class="wx-ring" aria-hidden="true"></span>
                <p>支付中</p>
              </div>
              <div v-else-if="phase === 'paying' && isAli" class="ali-load">
                <div class="ali-orbits" aria-hidden="true"><i></i><i></i></div>
                <p>正在付款</p>
              </div>
              <div v-else-if="phase === 'success' && !isAli" class="wx-success">
                <div class="wx-disc" aria-hidden="true">
                  <svg class="wx-tick" viewBox="0 0 48 48">
                    <path pathLength="1" d="M12 25.2 20.2 33.4 36.5 15.5" />
                  </svg>
                </div>
                <p class="wx-label">支付成功</p>
              </div>
              <div v-else-if="phase === 'success' && isAli" class="ali-success">
                <div class="ali-bloom" aria-hidden="true">
                  <i class="ali-ripple"></i>
                  <div class="ali-core">
                    <svg class="ali-tick" viewBox="0 0 48 48">
                      <path d="M12 25.2 20.2 33.4 36.5 15.5" />
                    </svg>
                  </div>
                </div>
                <p class="ali-label">付款成功</p>
                <span class="ali-sub">交易已完成</span>
              </div>
            </div>

            <div v-if="isAli && aliBar && errorText" class="ali-bar" role="alert">
              <Icon name="alert" :size="16" />
              <span>{{ errorText }}</span>
            </div>

            <div class="pay-keys" :class="{ away: showAnim }" aria-label="数字键盘">
              <template v-for="padKey in keys" :key="padKey">
                <span v-if="padKey === 'blank'" class="pay-key ghost" aria-hidden="true"></span>
                <button v-else-if="padKey === 'del'" class="pay-key del" type="button" aria-label="删除" :disabled="locked" @click="del">
                  <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                    <path d="M8.2 5.5h11.2a1.2 1.2 0 0 1 1.2 1.2v10.6a1.2 1.2 0 0 1-1.2 1.2H8.2L2.8 12l5.4-6.5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
                    <path d="m10.2 9.4 5.2 5.2M15.4 9.4l-5.2 5.2" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  </svg>
                </button>
                <button v-else class="pay-key" type="button" :aria-label="padKey" :disabled="locked" @click="push(padKey)">{{ padKey }}</button>
              </template>
            </div>
            <p class="sr-only" aria-live="polite">{{ live }}</p>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.pay-panel.sheet {
  background: #fff;
  max-height: 94%;
  overflow: hidden;
}
.pay-panel.sheet::before {
  content: "";
  position: absolute;
  left: 0; right: 0; top: 0;
  height: 3px;
  background: linear-gradient(90deg, #06ae56, #3ddc84);
  z-index: 1;
}
.pay-panel.alipay.sheet::before { background: linear-gradient(90deg, #1677ff, #69b1ff); }
.pay-panel:focus { outline: none; }
.pay-panel.is-busy { min-height: 520px; }
.pay-inner {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.pay-panel.is-busy .pay-inner { min-height: 520px; }
.pay-inner.is-ali-shake { animation: ali-nudge 520ms ease; }
@keyframes ali-nudge {
  0%, 100% { transform: translateX(0) rotate(0); }
  18% { transform: translateX(-7px) rotate(-0.5deg); }
  36% { transform: translateX(7px) rotate(0.5deg); }
  54% { transform: translateX(-4px) rotate(-0.2deg); }
  72% { transform: translateX(3px); }
}

.pay-top {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  padding: 2px 12px 0;
}
.pay-back {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 32px;
  padding: 0 8px 0 2px;
  border-radius: var(--r-pill);
  color: var(--text-2);
  font-size: var(--fs-sm);
  font-weight: 650;
}
.pay-back:disabled, .pay-close:disabled { opacity: 0.35; }
.pay-close { justify-self: end; }
.pay-brand { display: flex; align-items: center; gap: 8px; min-width: 0; }
.pay-brand img { width: 28px; height: 28px; border-radius: 8px; }
.pay-brand b { font-size: var(--fs-md); font-weight: 750; }

.pay-summary { text-align: center; padding: 10px 20px 0; }
.pay-amt {
  font-size: 40px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.05;
  color: var(--ink-900);
}
.pay-merchant {
  margin-top: 6px;
  padding: 0 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-3);
  font-size: var(--fs-sm);
}
.pay-ask { margin-top: 8px; color: var(--text-2); font-size: var(--fs-sm); }

.pay-mid {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 16px 18px 8px;
}
.pin-slot {
  width: 100%;
  max-height: 160px;
  overflow: hidden;
  opacity: 1;
  transform: none;
  filter: none;
  transition: max-height 420ms var(--ease-out), opacity 280ms ease, transform 420ms var(--ease-out);
}
.pin-slot.is-wx-collapse {
  max-height: 0;
  opacity: 0;
  transform: scale(0.92) translateY(10px);
}
.pin-slot.is-ali-out {
  max-height: 0;
  opacity: 0;
  transform: translateY(-14px);
  filter: blur(2px);
  transition: max-height 380ms var(--ease-out), opacity 260ms ease, transform 380ms var(--ease-out), filter 260ms ease;
}
.pin-slot.is-wx-back { transition: none; }
.pay-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 12px;
  color: var(--text-3);
  font-size: var(--fs-xs);
  line-height: 1.4;
  text-align: center;
}
.wx-err {
  max-width: 280px;
  margin: 10px auto 0;
  text-align: center;
  color: var(--danger-600);
  font-size: var(--fs-sm);
  font-weight: 700;
  line-height: 1.45;
}

.wx-load, .ali-load, .wx-success, .ali-success {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 148px;
}
.wx-load p, .ali-load p { margin: 14px 0 0; }
.wx-load p { font-size: var(--fs-base); font-weight: 650; color: #07C160; }
.ali-load p {
  font-size: var(--fs-sm);
  font-weight: 800;
  letter-spacing: 0.18em;
  color: #1677FF;
}
.wx-ring {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  border: 2px solid rgba(7, 193, 96, 0.16);
  border-top-color: #07C160;
  border-right-color: rgba(7, 193, 96, 0.5);
  animation: wx-spin 0.85s linear infinite;
}
@keyframes wx-spin { to { transform: rotate(360deg); } }

.ali-orbits { position: relative; width: 72px; height: 72px; }
.ali-orbits i {
  position: absolute;
  border-radius: 50%;
  border: 2.5px solid transparent;
}
.ali-orbits i:first-child {
  inset: 0;
  border-top-color: #1677FF;
  border-right-color: rgba(22, 119, 255, 0.28);
  animation: ali-yaw 1.25s ease-in-out infinite;
}
.ali-orbits i:last-child {
  inset: 12px;
  border-bottom-color: #1677FF;
  border-left-color: rgba(22, 119, 255, 0.22);
  animation: ali-yaw-back 1.25s ease-in-out infinite;
}
@keyframes ali-yaw {
  0% { transform: rotate(-40deg); }
  50% { transform: rotate(220deg); }
  100% { transform: rotate(-40deg); }
}
@keyframes ali-yaw-back {
  0% { transform: rotate(30deg); }
  50% { transform: rotate(-200deg); }
  100% { transform: rotate(30deg); }
}

.wx-disc {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #07C160;
  color: #fff;
  box-shadow: 0 14px 28px -12px rgba(7, 193, 96, 0.72);
  animation: wx-pop 0.46s var(--ease-spring) both;
}
@keyframes wx-pop {
  from { transform: scale(0.45); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
.wx-tick { width: 40px; height: 40px; }
.wx-tick path {
  fill: none;
  stroke: #fff;
  stroke-width: 3.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 1;
  stroke-dashoffset: 0;
  animation: wx-draw 0.58s 0.26s ease both;
}
@keyframes wx-draw {
  from { stroke-dashoffset: 1; }
  to { stroke-dashoffset: 0; }
}
.wx-label {
  margin: 14px 0 0;
  font-size: 16px;
  font-weight: 650;
  color: #07C160;
  animation: pay-rise 0.4s 0.5s var(--ease-out) both;
}

.ali-bloom {
  position: relative;
  width: 108px;
  height: 108px;
  display: grid;
  place-items: center;
}
.ali-ripple {
  position: absolute;
  inset: 6px;
  border-radius: 50%;
  background: rgba(22, 119, 255, 0.22);
  opacity: 0;
  animation: ali-bloom 0.74s var(--ease-out) both;
}
@keyframes ali-bloom {
  0% { transform: scale(0.15); opacity: 0; }
  58% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.22); opacity: 0; }
}
.ali-core {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #1677FF;
  box-shadow: 0 16px 32px -14px rgba(22, 119, 255, 0.78);
  position: relative;
  z-index: 1;
  animation: ali-core-in 0.5s var(--ease-out) both;
}
@keyframes ali-core-in {
  from { transform: scale(0.12); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
.ali-tick {
  width: 36px;
  height: 36px;
  animation: ali-drop 0.5s 0.4s cubic-bezier(0.18, 1.4, 0.4, 1) both;
}
@keyframes ali-drop {
  from { transform: translateY(-34px) scale(0.45); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}
.ali-tick path {
  fill: none;
  stroke: #fff;
  stroke-width: 3.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.ali-label {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #1677FF;
  animation: pay-rise 0.4s 0.62s var(--ease-out) both;
}
.ali-sub {
  margin-top: 4px;
  font-size: var(--fs-xs);
  letter-spacing: 0.22em;
  color: rgba(22, 119, 255, 0.72);
  animation: pay-rise 0.4s 0.74s var(--ease-out) both;
}
@keyframes pay-rise {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: none; }
}

.ali-bar {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 16px 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--danger-50);
  color: var(--danger-600);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
  animation: ali-bar-in 420ms var(--ease-out) both;
}
@keyframes ali-bar-in {
  from { opacity: 0; transform: translateX(36px); }
  to { opacity: 1; transform: translateX(0); }
}

.pay-keys {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 8px 14px calc(14px + env(safe-area-inset-bottom, 0px));
  background: #F3F1ED;
  max-height: 280px;
  overflow: hidden;
  opacity: 1;
  transform: none;
  transition: max-height 360ms var(--ease-out), opacity 220ms ease, transform 360ms var(--ease-out), padding 360ms ease;
}
.pay-keys.away {
  max-height: 0;
  opacity: 0;
  transform: translateY(16px);
  padding-top: 0;
  padding-bottom: 0;
  pointer-events: none;
}
.pay-key {
  height: 52px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 0 rgba(22, 20, 18, 0.04), 0 6px 12px -8px rgba(22, 20, 18, 0.28);
  font-family: var(--font-num);
  font-size: 22px;
  font-weight: 650;
  color: var(--ink-900);
  display: grid;
  place-items: center;
  user-select: none;
}
.pay-key:active { transform: scale(0.97); background: var(--ink-100); }
.pay-key.ghost { background: transparent; box-shadow: none; }
.pay-key.del { color: var(--ink-700); }
.pay-key.del svg { display: block; }

@media (prefers-reduced-motion: reduce) {
  .wx-ring,
  .ali-orbits i,
  .wx-disc,
  .wx-tick path,
  .wx-label,
  .ali-ripple,
  .ali-core,
  .ali-tick,
  .ali-label,
  .ali-sub,
  .ali-bar,
  .pay-inner.is-ali-shake {
    animation: none !important;
  }
  .wx-label, .ali-label, .ali-sub, .ali-tick, .wx-disc, .ali-core, .ali-bar { opacity: 1; transform: none; }
}
</style>
