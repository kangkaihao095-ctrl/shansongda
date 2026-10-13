<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import RoleBadge from '../components/RoleBadge.vue'
import RiderCard from '../components/RiderCard.vue'
import MemberBadge from '../components/MemberBadge.vue'
import CouponSheet from '../components/CouponSheet.vue'
import LocationSheet from '../components/LocationSheet.vue'
import PageHeader from '../components/PageHeader.vue'
import BottomSheet from '../components/BottomSheet.vue'
import Icon from '../components/Icon.vue'
import { APP_NAME, initials, yuan } from '../brand'
import { api, upload } from '../api'
import { MERCHANT_COVER, RIDER_COVER, merchantAvatarSrc, merchantCoverSrc, onImgError, riderAvatarSrc } from '../img'
import { shopIsOpen } from '../merchant'
import { formatClock, formatCoord } from '../status'
import {
  bootRiderLive, bumpLocation, changeInterval, riderLive, riderOnline, riderRemainingHours,
  riderWorkPct, toggleReport
} from '../riderLive'
import { loadMe, logout, session, toast } from '../session'

const router = useRouter()
const name = ref(session.me?.displayName || '')
const riderProfile = ref(session.me?.riderProfile || null)
const member = computed(() => session.me?.member || null)
const benefits = ref(null)
const unusedCount = ref(0)
const locOpen = ref(false)
const profileOpen = ref(false)
const renaming = ref(false)
const sheet = ref({ open: false, items: [], claimed: false })
const busy = ref(false)
const fileInput = ref(null)

const identity = computed(() => {
  const me = session.me
  if (!me) return ''
  if (me.role === 'MERCHANT') return `${me.merchant?.shopName || me.displayName} · 商家`
  if (me.role === 'RIDER') return `${me.displayName} · 骑手`
  return me.displayName || '闪送达用户'
})
const merchantOnline = computed(() => shopIsOpen(session.me?.merchant))
const merchantCover = computed(() => merchantCoverSrc(session.me?.merchant || { id: session.me?.userId }))
const meAvatarSrc = computed(() => {
  const me = session.me
  if (!me) return ''
  if (me.role === 'RIDER') return riderAvatarSrc(me.avatarUrl)
  if (me.role === 'MERCHANT') return merchantAvatarSrc(me.avatarUrl, me.merchant)
  return me.avatarUrl
})
const meAvatarFallback = computed(() => {
  if (session.me?.role === 'RIDER') return RIDER_COVER
  if (session.me?.role === 'MERCHANT') return MERCHANT_COVER
  return undefined
})

const planLine = computed(() => {
  const m = member.value
  if (!m?.subscribed) return ''
  if (m.autoRenew) return '连续包月 · 到期自动续 31 天'
  return m.planLabel || ''
})

/* —— 纯展示 —— */
const isUser = computed(() => session.me?.role === 'USER')
const isRider = computed(() => session.me?.role === 'RIDER')
const isMerchant = computed(() => session.me?.role === 'MERCHANT')
const scrolled = ref(false)
const saving = ref(false)
const avatarBusy = ref(false)
const maskedPhone = computed(() => String(session.me?.phone || '').replace(/^(\d{3})\d{4}(\d{4})$/, '$1 **** $2'))
const defaultAddress = computed(() => (session.me?.addresses || []).find((a) => a.isDefault) || session.me?.addresses?.[0])
const profileTitle = computed(() => {
  if (renaming.value) return '改昵称'
  return isRider.value ? '骑手资料' : '账号'
})

function onScroll(e) {
  scrolled.value = e.target.scrollTop > 48
}

async function onSaveName() {
  saving.value = true
  try { await saveName() } finally { saving.value = false }
}

async function onAvatarFile(e) {
  if (!e.target.files?.[0]) return
  avatarBusy.value = true
  try { await onFile(e) } finally { avatarBusy.value = false }
}

async function loadExtras() {
  if (session.me?.role === 'USER') {
    try {
      unusedCount.value = ((await api('/api/me/coupons?status=UNUSED')).data || []).length
    } catch { unusedCount.value = 0 }
    if (session.me?.member?.active) {
      try { benefits.value = (await api('/api/member/benefits')).data } catch { benefits.value = null }
    }
  }
  if (session.me?.role === 'RIDER') {
    await bootRiderLive()
    try { riderProfile.value = (await api('/api/riders/me/profile')).data } catch {
      riderProfile.value = session.me?.riderProfile || null
    }
  }
}

function openProfile() {
  name.value = session.me?.displayName || ''
  renaming.value = false
  profileOpen.value = true
}

function closeProfile() {
  profileOpen.value = false
  renaming.value = false
}

async function saveName() {
  try {
    await api('/api/me', { method: 'PUT', body: { displayName: name.value } })
    await loadMe()
    toast('昵称已更新')
    closeProfile()
  } catch (e) { toast(e.message, 'err') }
}

function pickAvatar() {
  fileInput.value?.click()
}

async function onFile(e) {
  const f = e.target.files?.[0]
  e.target.value = ''
  if (!f) return
  try {
    await upload('/api/me/avatar', f)
    await loadMe()
    toast('头像已更新')
    closeProfile()
  } catch (err) { toast(err.message, 'err') }
}

function out() {
  logout()
  router.replace('/login')
}

function openMemberPack() {
  const items = (benefits.value?.items || []).filter((it) => !it.claimed)
  const show = items.length ? items : (benefits.value?.items || [])
  if (!show.length) {
    toast('本周红包尚未就绪', 'err')
    return
  }
  if (items.length === 0) {
    toast('本周会员红包已领过')
    return
  }
  sheet.value = { open: true, items: show, claimed: false }
}

async function claimMember() {
  busy.value = true
  try {
    const res = await api('/api/member/claim', { method: 'POST' })
    sheet.value.claimed = true
    toast(res.data?.message || '已放入我的优惠券')
    benefits.value = (await api('/api/member/benefits')).data
    await loadMe()
    await loadExtras()
  } catch (e) { toast(e.message, 'err') }
  finally { busy.value = false }
}

async function applyLocation(payload) {
  try {
    if (payload.type === 'default') {
      await api(`/api/me/addresses/${payload.id}/default`, { method: 'PUT' })
    } else {
      await api('/api/me/location', { method: 'PUT', body: { lat: payload.lat, lon: payload.lon, detail: payload.detail } })
    }
    await loadMe()
    locOpen.value = false
    toast('默认地址已更新')
  } catch (e) { toast(e.message, 'err') }
}

onMounted(loadExtras)
</script>

<template>
  <div class="phone page me-page" :class="{ 'is-closed': isMerchant && !merchantOnline }">
    <PageHeader class="me-header" overlay :solid="scrolled" title="我的">
      <template #right><RoleBadge class="me-role" /></template>
    </PageHeader>

    <div class="phone-body" @scroll.passive="onScroll">
      <!-- 沉浸式身份区 -->
      <section class="me-hero">
        <img
          v-if="isRider"
          class="me-hero-img"
          :class="{ 'is-off': !riderOnline }"
          :src="RIDER_COVER"
          alt=""
        />
        <img
          v-else-if="isMerchant"
          class="me-hero-img"
          :class="{ 'is-off': !merchantOnline }"
          :src="merchantCover"
          alt=""
          :data-seed="'shop-' + session.me?.userId"
          :data-category="session.me?.merchant?.category || 'fresh'"
          :data-fallback-src="MERCHANT_COVER"
          @error="onImgError"
        />
        <i class="me-hero-glow" aria-hidden="true"></i>

        <button class="me-id" type="button" aria-label="查看和编辑资料" @click="openProfile">
          <span class="avatar me-avatar">
            <img
              v-if="isRider || isMerchant || session.me?.avatarUrl"
              :src="meAvatarSrc"
              alt=""
              data-seed="me-avatar"
              :data-fallback-src="meAvatarFallback"
              @error="onImgError"
            />
            <span v-else>{{ initials(session.me?.displayName) }}</span>
          </span>
          <span class="me-cam" aria-hidden="true"><Icon name="camera" :size="12" :stroke="2.2" /></span>
          <span class="me-id-main">
            <b class="me-name ellipsis">{{ identity }}</b>
            <span class="me-meta">
              <span v-if="maskedPhone" class="num">{{ maskedPhone }}</span>
              <MemberBadge v-if="isUser" :member="member" />
            </span>
          </span>
          <span class="me-id-go">资料<Icon name="chevron" :size="14" :stroke="2.4" /></span>
        </button>

        <div v-if="isRider" class="me-strip">
          <span class="me-chip">
            <span class="pulse-dot" :class="{ off: !riderOnline }"></span>{{ riderOnline ? '在线接单中' : '已下线' }}
          </span>
          <span class="me-chip"><Icon name="timer" :size="13" :stroke="2.2" />今日剩余 <b class="num">{{ riderRemainingHours }}</b>h</span>
        </div>
        <div v-else-if="isMerchant" class="me-strip">
          <span class="me-chip">
            <span class="pulse-dot" :class="{ off: !merchantOnline }"></span>{{ merchantOnline ? '营业中' : '已打烊' }}
          </span>
          <span class="me-chip"><Icon name="star" :size="13" :stroke="2.2" /><b class="num">{{ session.me?.merchant?.rating || 4.8 }}</b> 分</span>
        </div>
      </section>

      <div class="pad me-content">
        <!-- ================= 用户端 ================= -->
        <template v-if="isUser">
          <div
            v-reveal
            class="vip-card"
            :class="{ 'is-on': member?.subscribed, 'is-expired': member?.expired }"
            @click="member?.subscribed ? null : router.push('/member/pay')"
          >
            <i class="vip-sheen" aria-hidden="true"></i>
            <div class="vip-head">
              <span class="vip-crown"><Icon name="crown" :size="18" :stroke="2" /></span>
              <div class="grow">
                <div class="vip-kicker">{{ APP_NAME }} · VIP</div>
                <div class="vip-title">闪会员{{ member?.title ? '·' + member.title : '' }}</div>
              </div>
              <MemberBadge :member="member" />
            </div>

            <template v-if="member?.subscribed">
              <p class="vip-line">
                {{ planLine }}
                · {{ member.expired ? '已过期' : '到期 ' + String(member.expireAt || '').slice(0, 10) }}
                · 累计消费 <span class="num">{{ yuan(member.paidCentsNet || 0) }}</span>
              </p>
              <div class="vip-bar" role="progressbar" aria-label="会员等级进度" :aria-valuenow="member.progressPercent || 0" aria-valuemin="0" aria-valuemax="100">
                <i :style="{ width: (member.progressPercent || 0) + '%' }"></i>
              </div>
              <div class="vip-next">
                <span v-if="member.nextThresholdYuan > 0">距下一级还差 <b class="num">{{ member.nextThresholdYuan }}</b> 元</span>
                <span v-else>已满级 · 尊享全部权益</span>
                <span class="num">{{ member.progressPercent || 0 }}%</span>
              </div>
              <div class="vip-actions">
                <button v-if="member.active" class="btn btn-sm vip-btn" type="button" @click.stop="openMemberPack">
                  <Icon name="gift" :size="16" :stroke="2.2" />领取本周红包
                </button>
                <button class="btn btn-sm vip-btn-ghost" type="button" @click.stop="router.push('/member/pay')">续费</button>
              </div>
            </template>
            <template v-else>
              <ul class="vip-perks">
                <li><Icon name="gift" :size="15" />每周无门槛红包</li>
                <li><Icon name="bike" :size="15" />运费减免</li>
                <li><Icon name="crown" :size="15" />评论徽章</li>
              </ul>
              <p class="vip-line">开通后可领每周无门槛红包，评论展示闪会员徽章。</p>
              <button class="btn btn-block vip-btn mt-3" type="button">开通闪会员<Icon name="chevron" :size="16" :stroke="2.4" /></button>
            </template>
          </div>

          <nav class="me-grid" aria-label="常用功能">
            <button v-reveal="40" class="me-cell" type="button" @click="router.push('/coupons')">
              <span class="me-cell-ico"><Icon name="ticket" :size="20" /></span>
              <b class="num">{{ unusedCount }}</b>
              <span>红包卡券</span>
            </button>
            <button v-reveal="80" class="me-cell" type="button" @click="router.push('/me/reviews')">
              <span class="me-cell-ico"><Icon name="star" :size="20" /></span>
              <b class="me-cell-txt">查看</b>
              <span>我的评价</span>
            </button>
            <button v-reveal="120" class="me-cell" type="button" @click="router.push('/me/addresses')">
              <span class="me-cell-ico"><Icon name="pin" :size="20" /></span>
              <b class="num">{{ (session.me.addresses || []).length }}</b>
              <span>收货地址</span>
            </button>
            <button v-reveal="160" class="me-cell" type="button" @click="router.push('/member/pay')">
              <span class="me-cell-ico gold"><Icon name="crown" :size="20" /></span>
              <b class="me-cell-txt">{{ member?.subscribed && !member?.expired ? 'Lv' + (member.level || 1) : '开通' }}</b>
              <span>闪会员</span>
            </button>
          </nav>

          <div v-reveal class="list-group mt-4">
            <button class="list-row" type="button" @click="locOpen = true">
              <span class="row-ico"><Icon name="nav" :size="18" /></span>
              <span class="row-main">
                <b>收货定位</b>
                <span class="ellipsis">{{ defaultAddress?.detail || '未设置，推荐按默认地址计算距离' }}</span>
              </span>
              <span class="row-end">切换<Icon name="chevron" :size="16" /></span>
            </button>
            <button class="list-row" type="button" @click="openProfile">
              <span class="row-ico"><Icon name="user" :size="18" /></span>
              <span class="row-main"><b>账号资料</b><span>昵称、头像</span></span>
              <span class="row-end"><Icon name="chevron" :size="16" /></span>
            </button>
          </div>
        </template>

        <!-- ================= 骑手端 ================= -->
        <template v-if="isRider">
          <div v-if="riderLive.work.forcedOffline" v-reveal class="me-notice" role="alert">
            <Icon name="alert" :size="16" :stroke="2.2" />今日工时已满，已强制下线，次日可再上线
          </div>

          <section v-reveal class="card me-card">
            <div class="card-head">
              <h3 class="card-title"><Icon name="timer" :size="18" />工时规则</h3>
              <span class="tag">上限 8h</span>
            </div>
            <div class="work-row">
              <div class="work-big"><b class="num">{{ riderRemainingHours }}</b><small>h 剩余</small></div>
              <div class="grow">
                <div class="progress" role="progressbar" aria-label="今日已用工时" :aria-valuenow="riderWorkPct" aria-valuemin="0" aria-valuemax="100">
                  <i :style="{ width: riderWorkPct + '%' }"></i>
                </div>
                <div class="work-legend"><span>已用 {{ riderWorkPct }}%</span><span>次日 0 点重置</span></div>
              </div>
            </div>
            <p class="muted me-hint">每日上限 8 小时。剩余工时耗尽后系统强制下线，当日不可再上线，次日重置。今日剩余 {{ riderRemainingHours }}h。</p>
          </section>

          <section v-reveal="60" class="card me-card loc-card" :class="{ 'is-dim': !riderOnline, 'is-live': riderLive.autoReport }">
            <div class="loc-head">
              <span class="loc-ico" :class="{ on: riderLive.autoReport }"><Icon name="nav" :size="18" :stroke="2" /></span>
              <div class="grow">
                <div class="row">
                  <b class="t-title">位置自动上报</b>
                  <span class="pulse-dot" :class="{ off: !riderLive.autoReport, ping: riderLive.autoReport }"></span>
                </div>
                <div class="muted">开启后每 {{ riderLive.intervalSec }} 秒微调当前位置并上报，模拟在跑</div>
              </div>
              <button
                class="switch"
                :class="{ on: riderLive.autoReport }"
                type="button"
                role="switch"
                :aria-checked="riderLive.autoReport"
                :aria-label="riderLive.autoReport ? '关闭位置自动上报' : '开启位置自动上报'"
                :disabled="!riderOnline && !riderLive.autoReport"
                @click="toggleReport"
              ></button>
            </div>

            <label v-if="riderLive.autoReport || riderOnline" class="loc-interval">
              <span class="grow">上报间隔<small>3 – 30 秒</small></span>
              <span class="loc-input">
                <input v-model.number="riderLive.intervalSec" class="num" type="number" min="3" max="30" inputmode="numeric" aria-label="上报间隔秒数" @change="changeInterval" />
                <em>秒</em>
              </span>
            </label>

            <div class="loc-last" :class="{ live: riderLive.reporting }">
              <template v-if="riderLive.lastLoc">
                <span class="kicker">最新位置</span>
                <b class="num">{{ formatCoord(riderLive.lastLoc.lat) }}, {{ formatCoord(riderLive.lastLoc.lon) }}</b>
                <span class="loc-time">
                  <Icon name="clock" :size="12" />{{ formatClock(riderLive.lastLoc.at) }}{{ riderLive.autoReport ? ' · 自动上报中' : '' }}
                </span>
              </template>
              <span v-else class="muted">暂无位置记录，上线后可手动上报</span>
            </div>

            <button
              class="btn ghost btn-block mt-3"
              :class="{ dim: !riderOnline }"
              :disabled="!riderOnline"
              type="button"
              @click="bumpLocation(false)"
            >
              <span v-if="riderLive.reporting" class="spinner"></span>
              <Icon v-else name="target" :size="16" :stroke="2.2" />上报位置
            </button>
          </section>

          <section v-if="riderProfile" v-reveal="120" class="card me-card">
            <div class="card-head">
              <h3 class="card-title"><Icon name="shield" :size="18" />骑手资料</h3>
              <button class="link-btn" type="button" @click="openProfile">编辑<Icon name="chevron" :size="14" /></button>
            </div>
            <RiderCard :profile="riderProfile" compact hide-avatar />
          </section>
        </template>

        <!-- ================= 商家端 ================= -->
        <template v-if="isMerchant">
          <div v-reveal class="list-group shop-entry" :class="{ 'is-off': !merchantOnline }">
            <button class="list-row" type="button" @click="router.push('/shop')">
              <span class="row-ico"><Icon name="store" :size="18" /></span>
              <span class="row-main"><b>店铺经营</b><span>营业状态、商品与数据看板</span></span>
              <span class="row-end"><Icon name="chevron" :size="16" /></span>
            </button>
            <button class="list-row" type="button" @click="router.push('/orders')">
              <span class="row-ico"><Icon name="receipt" :size="18" /></span>
              <span class="row-main"><b>订单中心</b><span>接单、出餐、派单进度</span></span>
              <span class="row-end"><Icon name="chevron" :size="16" /></span>
            </button>
            <button class="list-row" type="button" @click="openProfile">
              <span class="row-ico"><Icon name="user" :size="18" /></span>
              <span class="row-main"><b>账号资料</b><span>昵称、头像</span></span>
              <span class="row-end"><Icon name="chevron" :size="16" /></span>
            </button>
          </div>
        </template>

        <button class="btn btn-danger btn-block me-logout" type="button" @click="out">
          <Icon name="logout" :size="17" :stroke="2" />退出登录
        </button>
        <p class="me-foot">{{ APP_NAME }} · {{ identity }}</p>
      </div>
    </div>

    <TabBar />
    <input ref="fileInput" type="file" accept="image/*" hidden @change="onAvatarFile" />

    <BottomSheet :open="profileOpen" :title="profileTitle" :subtitle="renaming ? '' : session.me?.phone" @close="closeProfile">
      <template v-if="!renaming">
        <div class="pf-head">
          <button class="pf-avatar" type="button" aria-label="更换头像" :disabled="avatarBusy" @click="pickAvatar">
            <span class="avatar">
              <img
                v-if="isRider || isMerchant || session.me?.avatarUrl"
                :src="meAvatarSrc"
                alt=""
                data-seed="me-avatar"
                :data-fallback-src="meAvatarFallback"
                @error="onImgError"
              />
              <span v-else>{{ initials(session.me?.displayName) }}</span>
            </span>
            <span class="pf-cam" aria-hidden="true">
              <span v-if="avatarBusy" class="spinner"></span>
              <Icon v-else name="camera" :size="13" :stroke="2.2" />
            </span>
          </button>
          <div class="grow">
            <b class="t-h3 ellipsis">{{ session.me?.displayName || '闪送达用户' }}</b>
            <div class="row mt-1"><RoleBadge /><MemberBadge v-if="isUser" :member="member" compact /></div>
          </div>
        </div>
        <RiderCard v-if="isRider && riderProfile" class="pf-rider" :profile="riderProfile" hide-avatar />
        <div class="list-group mt-4">
          <button class="list-row" type="button" @click="renaming = true">
            <span class="row-ico"><Icon name="edit" :size="18" /></span>
            <span class="row-main"><b>改昵称</b><span>{{ session.me?.displayName }}</span></span>
            <span class="row-end"><Icon name="chevron" :size="16" /></span>
          </button>
          <button class="list-row" type="button" :disabled="avatarBusy" @click="pickAvatar">
            <span class="row-ico"><Icon name="camera" :size="18" /></span>
            <span class="row-main"><b>换头像</b><span>{{ avatarBusy ? '上传中…' : '支持 JPG / PNG' }}</span></span>
            <span class="row-end"><span v-if="avatarBusy" class="spinner"></span><Icon v-else name="chevron" :size="16" /></span>
          </button>
        </div>
      </template>
      <template v-else>
        <label class="form-label" for="pf-name">新昵称</label>
        <input id="pf-name" v-model="name" class="field" placeholder="输入昵称" autocomplete="nickname" @keydown.enter="onSaveName" />
        <p class="muted mt-2">昵称会展示在订单与评价中。</p>
      </template>
      <template #footer>
        <template v-if="!renaming">
          <button class="btn ghost btn-block" type="button" @click="closeProfile">取消</button>
        </template>
        <template v-else>
          <button class="btn ghost grow" type="button" @click="renaming = false">取消</button>
          <button class="btn grow" type="button" :disabled="saving" @click="onSaveName">
            <span v-if="saving" class="spinner"></span>{{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
      </template>
    </BottomSheet>

    <LocationSheet :open="locOpen" @close="locOpen = false" @apply="applyLocation" />
    <CouponSheet
      :open="sheet.open"
      title="本周闪会员红包"
      subtitle="金底券面，确认后领取"
      :items="sheet.items"
      :busy="busy"
      :claimed="sheet.claimed"
      @claim="claimMember"
      @close="sheet.open = false"
    />
  </div>
</template>

<style scoped>
/* —— 沉浸式头部 —— */
.me-header { transition: background var(--dur-3) ease, color var(--dur-2) ease, border-color var(--dur-3) ease; }
.me-header:not(.is-solid) { background: transparent; color: var(--accent-ink); }
.me-header:not(.is-solid) .me-role { background: color-mix(in srgb, var(--surface) 30%, transparent); color: inherit; backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); }
.me-header:not(.is-solid) :deep(.header-title b) { opacity: 0; transform: translateY(4px); }
.me-header :deep(.header-title b) { transition: opacity var(--dur-3) ease, transform var(--dur-3) var(--ease-out); }

.me-hero {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  padding: calc(var(--header-h) + env(safe-area-inset-top, 0px) - 4px) var(--gutter) 64px;
  background: var(--hero-grad);
  color: var(--accent-ink);
}
.me-hero-img {
  position: absolute; inset: 0; z-index: -2;
  width: 100%; height: 100%;
  object-fit: cover;
  opacity: 0.32;
  mix-blend-mode: luminosity;
  animation: hero-zoom 1.2s var(--ease-out) both;
}
.me-hero-img.is-off { filter: grayscale(1); opacity: 0.18; }
.me-hero-glow {
  position: absolute; inset: 0; z-index: -1;
  background:
    radial-gradient(70% 90% at 100% 0%, color-mix(in srgb, var(--surface) 34%, transparent), transparent 62%),
    linear-gradient(180deg, transparent 55%, color-mix(in srgb, var(--canvas) 18%, transparent));
  pointer-events: none;
}
@keyframes hero-zoom { from { transform: scale(1.08); } to { transform: none; } }

.me-id {
  position: relative;
  display: flex; align-items: center; gap: 14px;
  width: 100%;
  padding: 6px 0;
  text-align: left;
  color: inherit;
  animation: rise var(--dur-4) var(--ease-out) both;
}
.me-avatar {
  width: 72px; height: 72px;
  font-size: var(--fs-2xl);
  background: var(--surface);
  color: var(--accent-strong);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--surface) 70%, transparent), 0 14px 28px -12px rgba(12, 11, 10, 0.45);
  transition: transform var(--dur-3) var(--ease-spring);
}
.me-id:hover .me-avatar { transform: scale(1.04); }
.me-id:active .me-avatar { transform: scale(0.96); }
.me-cam {
  position: absolute; left: 52px; top: 56px;
  width: 24px; height: 24px; border-radius: 50%;
  display: grid; place-items: center;
  background: var(--ink-900); color: #fff;
  box-shadow: 0 0 0 2px var(--surface);
}
.me-id-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.me-name { font-size: var(--fs-xl); font-weight: 800; letter-spacing: -0.01em; line-height: 1.2; }
.me-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: var(--fs-sm); font-weight: 600; opacity: 0.86; }
.me-id-go {
  display: inline-flex; align-items: center; gap: 1px;
  height: 28px; padding: 0 8px 0 12px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs); font-weight: 700;
  background: color-mix(in srgb, var(--surface) 24%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--surface) 30%, transparent);
  flex-shrink: 0;
}
.me-strip { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; animation: rise var(--dur-4) 80ms var(--ease-out) both; }
.me-chip {
  display: inline-flex; align-items: center; gap: 6px;
  height: 28px; padding: 0 12px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs); font-weight: 700;
  background: color-mix(in srgb, var(--surface) 16%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--surface) 22%, transparent);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.me-chip b { font-size: var(--fs-sm); }

.me-content { position: relative; z-index: 1; margin-top: -44px; }

/* —— 黑金会员卡 —— */
.vip-card {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  padding: 18px 18px 16px;
  border-radius: var(--r-xl);
  color: var(--brand-100);
  background:
    radial-gradient(90% 120% at 100% 0%, color-mix(in srgb, var(--brand-400) 26%, transparent), transparent 55%),
    linear-gradient(140deg, var(--ink-800) 0%, var(--ink-950) 70%);
  box-shadow: var(--sh-3), inset 0 0 0 1px color-mix(in srgb, var(--brand-300) 22%, transparent);
  cursor: pointer;
  transition: transform var(--dur-3) var(--ease-out), box-shadow var(--dur-3) ease;
}
.vip-card.is-on { cursor: default; }
@media (hover: hover) { .vip-card:not(.is-on):hover { transform: translateY(-2px); box-shadow: var(--sh-4); } }
.vip-card:not(.is-on):active { transform: scale(0.99); }
.vip-sheen {
  position: absolute; top: 0; bottom: 0; left: 0; z-index: -1;
  width: 45%;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--brand-200) 14%, transparent), transparent);
  animation: sheen 4.5s var(--ease-in-out) infinite;
  pointer-events: none;
}
.vip-head { display: flex; align-items: center; gap: 12px; }
.vip-crown {
  width: 38px; height: 38px; border-radius: 12px;
  display: grid; place-items: center; flex-shrink: 0;
  background: linear-gradient(135deg, var(--brand-200), var(--brand-500));
  color: var(--ink-900);
  box-shadow: 0 8px 18px -8px color-mix(in srgb, var(--brand-400) 80%, transparent);
}
.vip-kicker { font-size: var(--fs-2xs); font-weight: 800; letter-spacing: 0.18em; color: color-mix(in srgb, var(--brand-200) 60%, transparent); }
.vip-title {
  font-size: var(--fs-lg); font-weight: 800; letter-spacing: 0.01em;
  background: linear-gradient(90deg, var(--brand-100), var(--brand-300));
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.vip-line { margin-top: 12px; font-size: var(--fs-sm); line-height: var(--lh-normal); color: color-mix(in srgb, var(--brand-100) 72%, transparent); }
.vip-bar { height: 6px; margin-top: 12px; border-radius: var(--r-pill); background: color-mix(in srgb, var(--surface) 12%, transparent); overflow: hidden; }
.vip-bar i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--brand-200), var(--brand-500)); box-shadow: 0 0 10px color-mix(in srgb, var(--brand-400) 70%, transparent); transition: width 0.9s var(--ease-out); }
.vip-next { display: flex; justify-content: space-between; margin-top: 6px; font-size: var(--fs-xs); color: color-mix(in srgb, var(--brand-100) 62%, transparent); }
.vip-next b { color: var(--brand-300); font-size: var(--fs-sm); }
.vip-actions { display: flex; gap: 8px; margin-top: 14px; }
.vip-btn { background: linear-gradient(135deg, var(--brand-200), var(--brand-500)); color: var(--ink-900); box-shadow: 0 10px 22px -10px color-mix(in srgb, var(--brand-400) 80%, transparent); }
.vip-actions .vip-btn { flex: 1; }
.vip-btn-ghost { background: color-mix(in srgb, var(--surface) 10%, transparent); color: var(--brand-100); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand-200) 30%, transparent); }
.vip-btn-ghost::after { display: none; }
.vip-perks { list-style: none; margin: 14px 0 0; padding: 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.vip-perks li {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 4px;
  border-radius: var(--r-sm);
  background: color-mix(in srgb, var(--surface) 6%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand-200) 14%, transparent);
  font-size: var(--fs-xs); font-weight: 650;
}
.vip-perks .ico { color: var(--brand-300); }

/* —— 四宫格 —— */
.me-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: var(--s-4); }
.me-cell {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 14px 4px 12px;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
  transition: transform var(--dur-3) var(--ease-out), box-shadow var(--dur-3) ease;
}
@media (hover: hover) { .me-cell:hover { transform: translateY(-2px); box-shadow: var(--sh-2); } }
.me-cell:active { transform: scale(0.95); }
.me-cell-ico {
  width: 38px; height: 38px; border-radius: 13px;
  display: grid; place-items: center;
  background: var(--accent-soft); color: var(--accent-strong);
  margin-bottom: 2px;
}
.me-cell-ico.gold { background: linear-gradient(135deg, var(--ink-800), var(--ink-950)); color: var(--brand-300); }
.me-cell b { font-size: var(--fs-lg); font-weight: 800; line-height: 1.1; }
.me-cell .me-cell-txt { font-size: var(--fs-sm); }
.me-cell > span:last-child { font-size: var(--fs-xs); color: var(--text-3); font-weight: 600; }

/* —— 骑手 —— */
.me-notice {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: var(--s-3);
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--danger-50); color: var(--danger-600);
  font-size: var(--fs-sm); font-weight: 700;
  box-shadow: var(--sh-1);
}
.me-card + .me-card { margin-top: var(--s-3); }
.me-hint { margin-top: 12px; line-height: var(--lh-normal); }
.work-row { display: flex; align-items: center; gap: 16px; }
.work-big { display: flex; align-items: baseline; gap: 2px; flex-shrink: 0; }
.work-big b { font-size: var(--fs-3xl); font-weight: 800; line-height: 1; color: var(--accent-strong); letter-spacing: -0.03em; }
.work-big small { font-size: var(--fs-xs); color: var(--text-3); font-weight: 650; }
.work-legend { display: flex; justify-content: space-between; margin-top: 6px; font-size: var(--fs-2xs); color: var(--text-3); font-weight: 600; }

.loc-card { transition: box-shadow var(--dur-3) ease, opacity var(--dur-3) ease; }
.loc-card.is-live { box-shadow: 0 0 0 1.5px var(--accent), var(--sh-2); }
.loc-card.is-dim .loc-last, .loc-card.is-dim .loc-interval { opacity: 0.6; }
.loc-head { display: flex; align-items: flex-start; gap: 12px; }
.loc-head .muted { margin-top: 2px; line-height: 1.45; }
.loc-ico {
  width: 38px; height: 38px; border-radius: 12px; flex-shrink: 0;
  display: grid; place-items: center;
  background: var(--surface-sunken); color: var(--text-3);
  transition: background var(--dur-3) ease, color var(--dur-3) ease;
}
.loc-ico.on { background: var(--accent-grad); color: var(--accent-ink); box-shadow: var(--sh-accent); }
.loc-interval {
  display: flex; align-items: center; gap: 12px;
  margin-top: 14px; padding: 10px 12px 10px 14px;
  border-radius: var(--r-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--line);
  font-size: var(--fs-base); font-weight: 650;
}
.loc-interval small { display: block; font-size: var(--fs-2xs); color: var(--text-3); font-weight: 600; }
.loc-input {
  display: inline-flex; align-items: center; gap: 4px;
  height: 36px; padding: 0 10px 0 4px;
  border-radius: var(--r-sm);
  background: var(--surface);
  box-shadow: inset 0 0 0 1px var(--line-strong);
  transition: box-shadow var(--dur-2) ease;
}
.loc-input:focus-within { box-shadow: inset 0 0 0 1.5px var(--accent), 0 0 0 3px color-mix(in srgb, var(--accent) 16%, transparent); }
.loc-input input { width: 48px; height: 100%; border: 0; background: transparent; text-align: center; font-size: var(--fs-md); font-weight: 800; }
.loc-input em { font-style: normal; font-size: var(--fs-xs); color: var(--text-3); }
.loc-last {
  display: flex; flex-direction: column; gap: 2px;
  margin-top: 10px; padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--accent-soft);
  transition: box-shadow var(--dur-3) ease;
}
.loc-last.live { box-shadow: inset 0 0 0 1.5px var(--accent); }
.loc-last .kicker { color: var(--accent-strong); opacity: 0.8; }
.loc-last b { font-size: var(--fs-md); font-weight: 800; color: var(--text); }
.loc-time { display: inline-flex; align-items: center; gap: 4px; font-size: var(--fs-xs); color: var(--text-2); }

/* —— 商家 —— */
.shop-entry.is-off .row-ico { background: var(--surface-sunken); color: var(--text-3); }

/* —— 底部 —— */
.me-logout { margin-top: var(--s-6); }
.me-foot { margin-top: var(--s-3); text-align: center; font-size: var(--fs-xs); color: var(--text-3); }

/* —— 资料弹层 —— */
.pf-head { display: flex; align-items: center; gap: 14px; padding: 4px 0 2px; }
.pf-avatar { position: relative; flex-shrink: 0; }
.pf-avatar .avatar { width: 64px; height: 64px; }
.pf-cam {
  position: absolute; right: -2px; bottom: -2px;
  width: 24px; height: 24px; border-radius: 50%;
  display: grid; place-items: center;
  background: var(--ink-900); color: #fff;
  box-shadow: 0 0 0 2px var(--canvas);
}
.pf-cam .spinner { width: 12px; height: 12px; }
.pf-rider { margin-top: var(--s-4); }
.list-row:disabled { opacity: 0.6; }
.list-row .row-end .spinner { width: 14px; height: 14px; }
</style>
