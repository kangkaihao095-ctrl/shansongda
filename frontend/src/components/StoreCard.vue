<script setup>
import { computed } from 'vue'
import Icon from './Icon.vue'
import { imgSrc, onImgError } from '../img'
import { shopIsOpen } from '../merchant'
import { categoryName } from '../catalog'

const props = defineProps({
  shop: { type: Object, required: true },
  category: { type: String, default: '' }
})
const emit = defineEmits(['open'])

const cat = computed(() => props.shop.category || props.category || 'food')
const open = computed(() => shopIsOpen(props.shop))
const outOfRange = computed(() => props.shop.inRange === false)
const rating = computed(() => Number(props.shop.rating || props.shop.ratingAvg || 4.8).toFixed(1))
</script>

<template>
  <article
    class="store-card"
    :class="{ 'is-off': !open, 'is-far': outOfRange }"
    tabindex="0"
    role="link"
    :aria-label="shop.shopName"
    @click="emit('open')"
    @keydown.enter="emit('open')"
  >
    <div class="sc-cover">
      <img
        :src="imgSrc(shop.coverUrl, cat, 'shop-' + shop.id, 'shop')"
        :data-seed="'shop-' + shop.id"
        :data-category="cat"
        alt=""
        loading="lazy"
        @error="onImgError"
      />
      <div class="sc-tags">
        <span v-if="!open" class="tag tag-dark">休息中</span>
        <span v-else-if="outOfRange" class="tag tag-dark">超配送范围</span>
        <span v-else class="tag tag-dark"><i class="dot sc-open"></i>营业中</span>
      </div>
      <span v-if="shop.distanceKm != null" class="sc-dist"><Icon name="pin" :size="12" :stroke="2.2" />{{ shop.distanceKm }} km</span>
    </div>
    <div class="sc-body">
      <div class="sc-row">
        <h4 class="sc-name ellipsis">{{ shop.shopName }}</h4>
        <span class="sc-rating"><Icon name="star" :size="13" :stroke="0" class="sc-star" />{{ rating }}</span>
      </div>
      <div class="sc-meta">
        <span>{{ categoryName(cat) }}</span>
        <i></i>
        <span>月售 {{ shop.completedCount || 0 }}</span>
        <template v-if="shop.ratingCount">
          <i></i><span>{{ shop.ratingCount }} 条评价</span>
        </template>
      </div>
      <div class="sc-tags-row">
        <span v-if="shop.promo" class="tag">{{ shop.promo }}</span>
        <span class="tag tag-neutral">配送费按距离结算</span>
      </div>
      <p v-if="outOfRange" class="sc-warn"><Icon name="info" :size="13" />超出 {{ shop.maxKm || 5 }} km 配送范围，暂不可下单</p>
      <div v-if="shop.hitSkus?.length" class="sc-hits">
        <span class="sc-hit-label">命中</span>
        <span v-for="s in shop.hitSkus" :key="s.id" class="sc-hit">{{ s.name }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.store-card {
  position: relative;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-1);
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--dur-3) var(--ease-out), box-shadow var(--dur-3) var(--ease-out);
  outline: none;
}
@media (hover: hover) {
  .store-card:hover { transform: translateY(-3px); box-shadow: var(--sh-3); }
  .store-card:hover .sc-cover img { transform: scale(1.05); }
}
.store-card:active { transform: scale(0.99); }
.store-card:focus-visible { box-shadow: 0 0 0 2px var(--accent), var(--sh-2); }
.sc-cover { position: relative; height: 150px; overflow: hidden; background: var(--ink-150); }
.sc-cover img { width: 100%; height: 100%; object-fit: cover; transition: transform 700ms var(--ease-out), filter var(--dur-3) ease; }
.sc-cover::after { content: ""; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0, 0, 0, 0.18), transparent 35%, transparent 65%, rgba(0, 0, 0, 0.28)); pointer-events: none; }
.store-card.is-off .sc-cover img { filter: grayscale(0.55) brightness(0.85); }
.sc-tags { position: absolute; left: 10px; top: 10px; z-index: 1; display: flex; gap: 6px; }
.sc-open { background: #4ADE80 !important; box-shadow: 0 0 6px #4ADE80; }
.sc-dist {
  position: absolute; right: 10px; bottom: 10px; z-index: 1;
  display: inline-flex; align-items: center; gap: 3px;
  height: 24px; padding: 0 9px;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.92);
  color: var(--text);
  font-size: var(--fs-xs); font-weight: 750;
  font-family: var(--font-num);
  backdrop-filter: blur(8px);
}
.sc-body { padding: 12px 14px 14px; }
.sc-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.sc-name { font-size: var(--fs-lg); font-weight: 750; letter-spacing: -0.01em; min-width: 0; }
.sc-rating { display: inline-flex; align-items: center; gap: 3px; flex-shrink: 0; font-family: var(--font-num); font-size: var(--fs-md); font-weight: 800; color: var(--flame-600); }
.sc-star { fill: #FFB300; color: #FFB300; }
.sc-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; font-size: var(--fs-sm); color: var(--text-3); }
.sc-meta i { width: 3px; height: 3px; border-radius: 50%; background: var(--ink-300); }
.sc-tags-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.sc-warn { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: var(--fs-xs); color: var(--danger-600); font-weight: 600; }
.sc-hits { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--line-strong); }
.sc-hit-label { font-size: var(--fs-2xs); font-weight: 800; color: var(--text-3); letter-spacing: 0.08em; }
.sc-hit { height: 22px; padding: 0 8px; display: inline-flex; align-items: center; border-radius: 6px; background: var(--danger-50); color: var(--danger-600); font-size: var(--fs-xs); font-weight: 700; }
</style>
