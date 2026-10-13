<script setup>
import { computed, onMounted, ref } from 'vue'
import TabBar from '../components/TabBar.vue'
import CouponFace from '../components/CouponFace.vue'
import CouponSheet from '../components/CouponSheet.vue'
import CouponDetail from '../components/CouponDetail.vue'
import PageHeader from '../components/PageHeader.vue'
import SegTabs from '../components/SegTabs.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'
import { api } from '../api'
import { session, toast } from '../session'
import { loginGiftEligible, markLoginGiftClaimed, shanghaiDate } from '../coupon'

const tab = ref('UNUSED')
const list = ref([])
const mine = ref([])
const today = ref({ items: [], claimed: false, scene: null })
const loginGift = ref({ items: [], claimed: false, eligible: false })
const sheet = ref({ open: false, title: '', items: [], claimed: false, action: null })
const detail = ref(null)
const busy = ref(false)
const loading = ref(true)
const loadError = ref('')

const history = computed(() => mine.value.filter((c) => c.grantStatus === 'USED' || c.grantStatus === 'EXPIRED'))
const usable = computed(() => mine.value.filter((c) => c.grantStatus === 'UNUSED'))
const tabOptions = computed(() => [
  { key: 'UNUSED', label: `可使用 ${usable.value.length}` },
  { key: 'HISTORY', label: '已过期 / 已使用' }
])

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [a, unused, hist, t, g] = await Promise.all([
      api('/api/coupons'),
      api('/api/me/coupons?status=UNUSED'),
      api('/api/me/coupons?status=EXPIRED,USED'),
      api('/api/coupons/today').catch(() => ({ data: {} })),
      api('/api/coupons/login-gift').catch(() => ({ data: {} }))
    ])
    list.value = a.data || []
    mine.value = [...(unused.data || []), ...(hist.data || [])]
    today.value = t.data || { items: [] }
    loginGift.value = g.data || { items: [] }
  } catch (e) {
    loadError.value = e.message || '优惠券加载失败'
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

function openClaim(title, items, action) {
  sheet.value = { open: true, title, items, claimed: false, action }
}

async function runClaim() {
  if (!sheet.value.action) return
  busy.value = true
  try {
    await sheet.value.action()
    sheet.value.claimed = true
    await load()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

function claimCatalog(c) {
  if (c.claimed) return
  openClaim(c.name || '领取优惠券', [c], async () => {
    if (c.activityId) {
      const res = await api(`/api/activities/${c.activityId}/grab-coupon`, { method: 'POST' })
      toast(res.replayed ? '已抢过该券' : '抢券成功')
    } else {
      await api(`/api/coupons/${c.id}/claim`, { method: 'POST' })
      toast('已放入我的优惠券')
    }
  })
}

function claimToday() {
  if (today.value.claimed) return
  openClaim('今日券包', today.value.items || [], async () => {
    const res = await api('/api/coupons/grant-session', { method: 'POST' })
    toast(res.data?.message || (res.data?.replayed ? '今日已领过' : '已放入我的优惠券'))
  })
}

function claimLogin() {
  if (!loginGiftEligible(loginGift.value)) return
  openClaim(loginGift.value.title || '登录礼', loginGift.value.items || [], async () => {
    const res = await api('/api/coupons/login-gift', { method: 'POST' })
    markLoginGiftClaimed(session.me?.userId, loginGift.value.date || shanghaiDate())
    toast(res.data?.message || '已放入我的优惠券')
  })
}

onMounted(load)
</script>

<template>
  <div class="phone page">
    <PageHeader back title="优惠券" subtitle="结算时自动挑最划算的" />
    <div class="phone-body pad">
      <div v-if="loading && !list.length && !mine.length" class="stack-3" aria-busy="true" aria-label="加载中">
        <div class="skel" style="height:120px;border-radius:20px"></div>
        <div class="skel" style="height:88px"></div>
        <div class="skel" style="height:88px"></div>
      </div>

      <EmptyState v-else-if="loadError && !list.length && !mine.length" kind="error" title="优惠券加载失败" :hint="loadError">
        <button class="btn btn-sm" type="button" @click="load"><Icon name="refresh" :size="16" />重试</button>
      </EmptyState>

      <template v-else>
        <section v-if="loginGiftEligible(loginGift)" class="gift-card" @click="claimLogin()">
          <div class="gift-copy">
            <span class="pill tag-brand"><Icon name="gift" :size="12" />登录礼</span>
            <b>{{ loginGift.title || '今日登录礼' }}</b>
            <p>点开券面领取，同一天只发一次</p>
          </div>
          <div class="coupon-spread packed">
            <CouponFace v-for="it in loginGift.items" :key="it.code" :coupon="it" compact mode="cover" />
          </div>
          <button class="btn btn-block" type="button" @click.stop="claimLogin">领取登录礼</button>
        </section>

        <section class="section">
          <div class="section-head">
            <h3 class="section-title"><Icon name="sparkles" :size="18" class="text-accent" />今日可领</h3>
          </div>
          <article class="card" :class="{ 'is-interactive': !today.claimed && today.items?.length }" @click="!today.claimed && today.items?.length && claimToday()">
            <div v-if="today.items?.length" class="coupon-spread packed">
              <CouponFace v-for="it in today.items" :key="it.code" :coupon="it" compact mode="cover" />
            </div>
            <EmptyState v-else bare title="今日暂无券包" hint="活跃度达标后，这里会出现可领的券" />
            <button v-if="today.items?.length" class="btn btn-block mt-3" :disabled="today.claimed" type="button" @click.stop="claimToday">
              {{ today.claimed ? '今日已领' : '领取今日券包' }}
            </button>
          </article>
        </section>

        <section class="section">
          <div class="section-head">
            <h3 class="section-title"><Icon name="bolt" :size="18" class="text-accent" />抢券</h3>
            <span class="section-more">限量，领完即止</span>
          </div>
          <div v-if="list.length" class="stack-2">
            <button v-for="c in list" :key="c.id" class="claim-row" type="button" :disabled="c.claimed" @click="claimCatalog(c)">
              <CouponFace :coupon="c" compact mode="cover" />
              <span class="grow">
                <b>{{ c.name || '优惠券' }}</b>
                <small>{{ c.activityId ? '限量抢券，领过不会再扣' : '放入我的优惠券' }}</small>
              </span>
              <span class="btn btn-sm" :class="{ ghost: c.claimed }">{{ c.claimed ? '已领取' : '领取' }}</span>
            </button>
          </div>
          <EmptyState v-else bare title="暂无可领优惠券" hint="新的抢券活动上线后会出现在这里" />
        </section>

        <section class="section">
          <SegTabs v-model="tab" :options="tabOptions" label="我的优惠券" />
          <div class="stack-2 mt-3">
            <template v-if="tab === 'UNUSED'">
              <button v-for="c in usable" :key="c.grantId" class="wallet-row" type="button" @click="detail = c">
                <CouponFace :coupon="c" compact mode="wallet" />
                <Icon name="chevron" :size="16" />
              </button>
              <EmptyState v-if="!usable.length" title="暂无可用券" hint="领取后可在结算时抵扣商品金额，不抵运费" />
            </template>
            <template v-else>
              <button v-for="c in history" :key="c.grantId" class="wallet-row is-dim" type="button" @click="detail = c">
                <CouponFace :coupon="c" compact mode="wallet" />
                <span class="pill off">{{ c.grantStatus === 'USED' ? '已使用' : '已过期' }}</span>
              </button>
              <EmptyState v-if="!history.length" title="还没有历史券" hint="用过或过期的券会留在这里" />
            </template>
          </div>
        </section>
      </template>
    </div>
    <TabBar />
    <CouponSheet
      :open="sheet.open"
      :title="sheet.title"
      :items="sheet.items"
      :busy="busy"
      :claimed="sheet.claimed"
      @claim="runClaim"
      @close="sheet.open = false"
    />
    <CouponDetail :open="!!detail" :coupon="detail" @close="detail = null" />
  </div>
</template>

<style scoped>
.gift-card {
  margin-top: var(--s-4);
  padding: 16px;
  border-radius: var(--r-xl);
  background:
    radial-gradient(80% 80% at 100% 0%, rgba(255, 194, 31, 0.45), transparent 60%),
    var(--grad-brand-soft);
  box-shadow: var(--sh-2);
  cursor: pointer;
}
.gift-copy b { display: block; margin-top: 8px; font-size: var(--fs-lg); }
.gift-copy p { margin-top: 2px; font-size: var(--fs-xs); color: var(--text-2); }
.gift-card .btn { margin-top: 8px; }
.claim-row, .wallet-row {
  display: flex; align-items: center; gap: 12px;
  width: 100%;
  padding: 10px 12px;
  text-align: left;
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--sh-1);
}
.claim-row:disabled { opacity: 0.72; }
.claim-row b, .wallet-row b { display: block; font-size: var(--fs-md); }
.claim-row small { display: block; margin-top: 2px; font-size: var(--fs-xs); color: var(--text-3); }
.claim-row .btn { pointer-events: none; }
.wallet-row { cursor: pointer; }
.wallet-row.is-dim { opacity: 0.72; }
</style>
