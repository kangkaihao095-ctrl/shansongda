#!/usr/bin/env node
/**
 * 下载门头 / 菜品图到 frontend/public/images/{shops,dishes}/，每 ID 一张，禁止复用。
 * 源：picsum.photos/seed/ssd-shop-{id}|ssd-sku-{id}（seed 不同则图不同）。
 * 用法：node scripts/fetch-images.mjs [--dishes] [--max-dishes=200]
 */
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const shopsDir = join(root, 'public/images/shops')
const dishesDir = join(root, 'public/images/dishes')
const wantDishes = process.argv.includes('--dishes') || process.argv.some((a) => a.startsWith('--max-dishes'))
const maxDishesArg = process.argv.find((a) => a.startsWith('--max-dishes='))
const maxDishes = maxDishesArg ? Number(maxDishesArg.split('=')[1]) : (wantDishes ? 5040 : 0)

function mysql(sql) {
  const env = { ...process.env, DOCKER_HOST: process.env.DOCKER_HOST || 'unix:///Users/lumengkang/.colima/default/docker.sock' }
  const out = execFileSync('docker', [
    'exec', 'ssd-mysql', 'mysql', '-ushansuda', '-pshansuda', '-N', '-e', sql
  ], { encoding: 'utf8', env })
  return out.split('\n').map((l) => l.trim()).filter(Boolean)
}

async function download(url, dest, tries = 3) {
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url, { redirect: 'follow' })
      if (!res.ok) throw new Error('HTTP ' + res.status)
      const buf = Buffer.from(await res.arrayBuffer())
      if (buf.length < 800) throw new Error('too small')
      await mkdir(dirname(dest), { recursive: true })
      await writeFile(dest, buf)
      return true
    } catch (err) {
      if (i === tries - 1) {
        console.warn('skip', url, err.message)
        return false
      }
      await new Promise((r) => setTimeout(r, 400 * (i + 1)))
    }
  }
  return false
}

async function pool(items, limit, fn) {
  let i = 0
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (i < items.length) {
      const cur = items[i++]
      await fn(cur)
    }
  })
  await Promise.all(workers)
}

const shopIds = mysql('SELECT user_id FROM ssd_account.merchant ORDER BY user_id')
let skuIds = []
if (maxDishes > 0) {
  skuIds = mysql('SELECT id FROM ssd_account.merchant_sku ORDER BY id').slice(0, maxDishes)
}

console.log(`shops=${shopIds.length} dishes=${skuIds.length}`)
await mkdir(shopsDir, { recursive: true })
let shopOk = 0
await pool(shopIds, 6, async (id) => {
  const ok = await download(`https://picsum.photos/seed/ssd-shop-${id}/800/600`, join(shopsDir, `${id}.jpg`))
  if (ok) shopOk++
})
let skuOk = 0
if (skuIds.length) {
  await mkdir(dishesDir, { recursive: true })
  await pool(skuIds, 8, async (id) => {
    const ok = await download(`https://picsum.photos/seed/ssd-sku-${id}/800/600`, join(dishesDir, `${id}.jpg`))
    if (ok) skuOk++
  })
}
console.log(`downloaded shops=${shopOk}/${shopIds.length} dishes=${skuOk}/${skuIds.length}`)
console.log('重启 ssd-account 后 CatalogSeeder 会优先改写为本地 /images/shops|dishes/{id}.jpg')
