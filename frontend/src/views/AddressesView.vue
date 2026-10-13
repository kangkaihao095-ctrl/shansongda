<script setup>
import { onMounted, ref } from 'vue'
import TabBar from '../components/TabBar.vue'
import PageHeader from '../components/PageHeader.vue'
import BottomSheet from '../components/BottomSheet.vue'
import EmptyState from '../components/EmptyState.vue'
import Icon from '../components/Icon.vue'
import { api } from '../api'
import { loadMe, session, toast } from '../session'

const items = ref([])
const form = ref({ id: null, lat: 31.2304, lon: 121.4737, detail: '' })
const editing = ref(false)
const busy = ref(false)
const loading = ref(true)
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    items.value = (await api('/api/me/addresses')).data || session.me?.addresses || []
  } catch (e) {
    loadError.value = e.message || '地址加载失败'
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

function startAdd() {
  form.value = { id: null, lat: 31.2304, lon: 121.4737, detail: '' }
  editing.value = true
}

function startEdit(a) {
  form.value = { id: a.id, lat: a.lat, lon: a.lon, detail: a.detail }
  editing.value = true
}

async function save() {
  if (!form.value.detail?.trim()) return toast('请填写详细地址', 'err')
  busy.value = true
  try {
    if (form.value.id) {
      await api('/api/me/addresses/' + form.value.id, {
        method: 'PUT',
        body: { lat: Number(form.value.lat), lon: Number(form.value.lon), detail: form.value.detail }
      })
      toast('地址已更新')
    } else {
      await api('/api/me/addresses', {
        method: 'POST',
        body: { lat: Number(form.value.lat), lon: Number(form.value.lon), detail: form.value.detail }
      })
      toast('地址已添加')
    }
    editing.value = false
    await loadMe()
    await load()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}

async function setDefault(id) {
  try {
    await api('/api/me/addresses/' + id + '/default', { method: 'PUT' })
    await loadMe()
    await load()
    toast('已设为默认')
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function remove(id) {
  try {
    await api('/api/me/addresses/' + id, { method: 'DELETE' })
    await loadMe()
    await load()
    toast('已删除')
  } catch (e) {
    toast(e.message, 'err')
  }
}

onMounted(load)
</script>

<template>
  <div class="phone page">
    <PageHeader back="/me" title="收货地址" subtitle="推荐与配送都按这里计算距离" />
    <div class="phone-body pad">
      <div v-if="loading && !items.length" class="stack-3" aria-busy="true" aria-label="加载中">
        <div v-for="n in 2" :key="n" class="skel" style="height:108px;border-radius:18px"></div>
      </div>
      <EmptyState v-else-if="loadError && !items.length" kind="error" title="地址加载失败" :hint="loadError">
        <button class="btn btn-sm" type="button" @click="load"><Icon name="refresh" :size="16" />重试</button>
      </EmptyState>
      <template v-else>
        <p class="addr-note"><Icon name="pin" :size="14" />默认坐标落在上海黄浦 / 外滩一带，和演示路网一致。</p>
        <article v-for="a in items" :key="a.id" class="addr-card">
          <div class="row-between">
            <b>{{ a.detail }}</b>
            <span v-if="a.isDefault" class="pill tag-success">默认</span>
          </div>
          <div class="muted mt-1 num">{{ a.lat }}, {{ a.lon }}</div>
          <div class="row mt-3">
            <button class="btn btn-sm ghost" type="button" @click="startEdit(a)"><Icon name="edit" :size="14" />编辑</button>
            <button v-if="!a.isDefault" class="btn btn-sm ghost" type="button" @click="setDefault(a.id)">设为默认</button>
            <button class="btn btn-sm btn-danger" type="button" @click="remove(a.id)"><Icon name="trash" :size="14" />删除</button>
          </div>
        </article>
        <EmptyState v-if="!items.length" title="还没有地址" hint="添加后，首页推荐和结算运费会按这个点计算">
          <button class="btn btn-sm" type="button" @click="startAdd"><Icon name="plus" :size="14" />新增地址</button>
        </EmptyState>
        <button v-else class="btn btn-block mt-3" type="button" @click="startAdd"><Icon name="plus" :size="16" />新增地址</button>
      </template>
    </div>
    <TabBar />
    <BottomSheet :open="editing" :title="form.id ? '编辑地址' : '新增地址'" subtitle="用于推荐距离、运费和骑手导航" @close="editing = false">
      <label class="form-label" for="addr-detail">详细地址</label>
      <input id="addr-detail" v-model="form.detail" class="field" maxlength="255" placeholder="小区、楼栋、门牌" />
      <div class="row mt-2">
        <div class="grow">
          <label class="form-label" for="addr-lat">纬度</label>
          <input id="addr-lat" v-model="form.lat" class="field" inputmode="decimal" placeholder="31.2304" />
        </div>
        <div class="grow">
          <label class="form-label" for="addr-lon">经度</label>
          <input id="addr-lon" v-model="form.lon" class="field" inputmode="decimal" placeholder="121.4737" />
        </div>
      </div>
      <template #footer>
        <button class="btn ghost" type="button" @click="editing = false">取消</button>
        <button class="btn grow" type="button" :disabled="busy" @click="save">
          <span v-if="busy" class="spinner"></span>{{ busy ? '保存中…' : '保存地址' }}
        </button>
      </template>
    </BottomSheet>
  </div>
</template>

<style scoped>
.addr-note {
  display: flex; align-items: flex-start; gap: 6px;
  margin: var(--s-4) 0 var(--s-3);
  font-size: var(--fs-xs); color: var(--text-2); line-height: 1.5;
}
.addr-note .ico { margin-top: 1px; color: var(--accent); }
.addr-card {
  padding: 14px;
  border-radius: var(--r-xl);
  background: var(--surface);
  box-shadow: var(--sh-1);
}
.addr-card + .addr-card { margin-top: 10px; }
.addr-card b { font-size: var(--fs-md); line-height: 1.4; }
</style>
