<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TabBar from '../components/TabBar.vue'
import { api } from '../api'
import { goBack, loadMe, session, toast } from '../session'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const items = ref([])
const form = ref({ id: null, lat: 31.2304, lon: 121.4737, detail: '' })
const editing = ref(false)
const busy = ref(false)

async function load() {
  try {
    items.value = (await api('/api/me/addresses')).data || session.me?.addresses || []
  } catch (e) {
    toast(e.message, 'err')
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
    <header class="frost pad row">
      <button class="back-btn" type="button" @click="goBack(router, '/me')">← 返回</button>
      <b class="page-title" style="font-size:18px">收货地址</b>
    </header>
    <div class="phone-body pad">
      <article v-for="a in items" :key="a.id" class="card" style="margin-bottom:10px">
        <div class="row" style="justify-content:space-between">
          <b>{{ a.detail }}</b>
          <span v-if="a.isDefault" class="status-pill live">默认</span>
        </div>
        <div class="muted">{{ a.lat }}, {{ a.lon }}</div>
        <div class="row" style="margin-top:8px">
          <button class="btn ghost" type="button" @click="startEdit(a)">编辑</button>
          <button v-if="!a.isDefault" class="btn ghost" type="button" @click="setDefault(a.id)">设默认</button>
          <button class="btn ghost" type="button" @click="remove(a.id)">删除</button>
        </div>
      </article>
      <EmptyState v-if="!items.length" title="还没有地址" hint="添加后用于推荐和配送" />
      <button class="btn" style="width:100%;margin-top:8px" type="button" @click="startAdd">新增地址</button>
      <div v-if="editing" class="card" style="margin-top:12px">
        <b>{{ form.id ? '编辑地址' : '新增地址' }}</b>
        <input v-model="form.detail" class="field" placeholder="详细地址" style="margin-top:8px;width:100%" />
        <div class="row" style="margin-top:8px;gap:8px">
          <input v-model="form.lat" class="field" placeholder="纬度" />
          <input v-model="form.lon" class="field" placeholder="经度" />
        </div>
        <div class="row" style="margin-top:10px">
          <button class="btn" :disabled="busy" type="button" @click="save">保存</button>
          <button class="btn ghost" type="button" @click="editing = false">取消</button>
        </div>
      </div>
    </div>
    <TabBar />
  </div>
</template>
