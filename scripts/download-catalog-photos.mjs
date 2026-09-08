#!/usr/bin/env node
/**
 * 把可商用真实照片下到 frontend/public/images，供浏览器只打本站。
 * 校验 JPEG 魔数，禁止把 HTML 存成图。
 */
import { execSync } from 'node:child_process'
import { copyFile, mkdir, writeFile } from 'node:fs/promises'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const IMG = path.join(ROOT, 'frontend/public/images')
const UA =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
const WIKI_UA = 'ShansudaCatalog/1.0 (local demo catalog seed; https://localhost)'
const SHOP_N = 15
const DISH_N = 80
const CONCURRENCY = 8

const CATS = {
  food: {
    shopQ: ['chinese restaurant', 'restaurant facade', 'restaurant storefront', 'restaurant interior'],
    dishQ: ['chinese cuisine', 'dumplings', 'noodles bowl', 'fried rice', 'dim sum', 'peking duck', 'mapo tofu'],
    pexelsShop: [262978, 67468, 941861, 260922, 1307698, 1581384, 1579739, 2290070, 2403391, 2696064, 2814828, 1449773, 1565982, 239975, 941864],
    pexelsDish: [1640777, 1279330, 376464, 262959, 1639562, 1624487, 958545, 1410235, 2474661, 2097090, 1199957, 1099680, 1660030, 3184192, 3338681, 3535383, 357756, 4253312, 566566, 675951, 718742, 842571, 1059905, 1211887, 1435895, 1487511, 1640770, 1640772, 1640774, 1833336, 2338407, 2347311, 2664216, 2736520, 2878741, 2983101, 3756523, 4551832, 5409015, 5718010, 6287295, 6419722, 1267320, 1351238, 1437267, 1527603, 1559778, 1600711, 2284166, 2454533, 3026808, 3738730],
    unsplashShop: ['photo-1517248135467-4c7edcad34c4', 'photo-1555396273-367ea4eb4db5', 'photo-1552566626-52f8b828add9', 'photo-1414235077428-338989a2e8c0', 'photo-1466978913421-dad2ebd01d17', 'photo-1424845830515-b93fa043b713', 'photo-1550966871-3ed3cdb5ed0c', 'photo-1544148103-0773bf10d330', 'photo-1467003909585-2f8a72700288', 'photo-1559339352-11d035aa65de'],
    unsplashDish: ['photo-1563245372-f21724e3856d', 'photo-1582878826629-29b7ad1cdc43', 'photo-1569718212165-3a8278d5f624', 'photo-1526318896980-cf78c088247c', 'photo-1496116218417-1a781b1c416c', 'photo-1504674900247-0877df9cc836', 'photo-1603133872878-684f208fb84b', 'photo-1546069901-ba9599a7e63c', 'photo-1567620905732-2d1ec7ab7445', 'photo-1512058564366-18510be2db19']
  },
  dessert: {
    shopQ: ['bakery storefront', 'cake shop', 'cafe exterior', 'patisserie'],
    dishQ: ['cake', 'pastry', 'bubble tea', 'milk tea', 'macaron', 'cupcake', 'dessert plate'],
    pexelsShop: [1855214, 302899, 373639, 205961, 1721932, 230743, 374018, 683039, 1307692, 2144112, 1024359, 1855215, 2074130, 2396220, 302901],
    pexelsDish: [1126359, 291528, 1055272, 140831, 1702373, 1721932, 2144112, 1055271, 1857684, 1998632, 205961, 2144112, 291528, 3026808, 1857685, 1998633, 230325, 2915282, 3776942, 3992131, 4109998, 434295, 461431, 1028714, 1098592, 1126359, 291528, 3776947, 3992134, 4109996],
    unsplashShop: ['photo-1555507036-ab1f4038808a', 'photo-1495474472287-4d71bcdd2085', 'photo-1445116572660-236099ec97a0', 'photo-1509042239860-f550ce710b93', 'photo-1453614512568-7af4e5b5d6a3'],
    unsplashDish: ['photo-1578985545062-69928b1d9587', 'photo-1565958011703-44f9829ba187', 'photo-1486427944299-d1955d23e34d', 'photo-1464349095431-e9a21285b5f3', 'photo-1587314168485-3236d6710814', 'photo-1551024601-bec78aea704b', 'photo-1515823064-d6e0c04616a7', 'photo-1558857563-b371033873b8']
  },
  market: {
    shopQ: ['supermarket interior', 'convenience store', 'grocery store', 'supermarket aisle'],
    dishQ: ['supermarket shelf', 'bottled water', 'grocery products', 'snacks packaged', 'household products supermarket'],
    pexelsShop: [264636, 373893, 1005638, 1327838, 2292919, 264637, 264636, 4393021, 3962285, 3962292, 4386464, 264548, 264511, 2292918, 3962287],
    pexelsDish: [416528, 1283219, 1283217, 1340116, 1435904, 1435907, 1660030, 264636, 373893, 3962285, 4099235, 4099238, 4198023, 4393021, 4963955, 5632371, 5632397, 5632402, 5632379, 5946071, 5946083, 1283216, 1340116, 1435904, 1640777, 2292919, 373893, 4099234, 4391470, 5632371, 5946075],
    unsplashShop: ['photo-1542838132-92c53300491e', 'photo-1578916171728-46686eac8d58', 'photo-1604719312564-2872c90b0aca', 'photo-1583258292688-d0213dc5a6c8', 'photo-1534723452862-4c874018d66d'],
    unsplashDish: ['photo-1604719312564-2872c90b0aca', 'photo-1583258292688-d0213dc5a6c8', 'photo-1550989460-0adf9ea622e2', 'photo-1579113800032-c38bd763ce4c', 'photo-1610832958506-aa56368176cf']
  },
  fresh: {
    shopQ: ['fruit market', 'greengrocer', 'fish market', 'produce stall'],
    dishQ: ['fresh fruit', 'fresh vegetables', 'seafood display', 'strawberries', 'watermelon', 'leafy greens'],
    pexelsShop: [1132047, 1300972, 1656663, 2255935, 143133, 2255936, 264537, 1300973, 2255934, 1656664, 1431332, 2255937, 373893, 1132046, 2255933],
    pexelsDish: [1132047, 143133, 1300972, 1656663, 2255935, 760281, 1132046, 2255936, 1656664, 760281, 1128678, 1414110, 1414110, 1435904, 1656666, 2255935, 373893, 533280, 533280, 760281, 1132047, 1128678, 1414110, 143133, 2255935, 533280, 760281, 1656663, 1300972, 1128678],
    unsplashShop: ['photo-1488459716781-31db52582fe9', 'photo-1542838132-92c53300491e', 'photo-1488459716781-31db52582fe9', 'photo-1506802913710-40e2e66339c9', 'photo-1464965911861-746a04b4bca6'],
    unsplashDish: ['photo-1610832958506-aa56368176cf', 'photo-1619566636858-adf3ef46400b', 'photo-1560806887-1e4cd0b6cbd6', 'photo-1601004890684-d8cbf843f8f1', 'photo-1519996529931-28324d484a8b', 'photo-1596591606975-97c5c5045c4b', 'photo-1576045057995-568f588f82fb', 'photo-1607301405390-8ce67cdc3c0f']
  },
  pharma: {
    shopQ: ['pharmacy storefront', 'drugstore', 'pharmacy interior', 'chemist shop'],
    dishQ: ['medicine pills', 'medicine bottles', 'capsules tablets', 'prescription medication', 'pharmacy shelves'],
    pexelsShop: [3873174, 3873146, 3873193, 139398, 40568, 3873172, 5910953, 3873167, 6129683, 3873194, 263402, 3873173, 7088530, 3873184, 7615465],
    pexelsDish: [3683074, 3683098, 3683107, 208512, 360622, 3683041, 3873195, 3683073, 3683101, 3786126, 3825586, 4386466, 5206946, 5726706, 5863395, 3683056, 3825527, 4386467, 5910970, 7615463, 3873193, 3873146, 3683089, 4047186, 4210611, 5327585, 5852460, 6129043, 3683074, 3683098],
    unsplashShop: ['photo-1585435557343-3b092031a831', 'photo-1576602976047-174e57a47881', 'photo-1587854692152-cbe660dbde88', 'photo-1631549916768-4119b2e5f926', 'photo-1471864190281-a93a3070b6de'],
    unsplashDish: ['photo-1584308666744-24d5c474f2ae', 'photo-1587854692152-cbe660dbde88', 'photo-1584308666744-24d5c474f2ae', 'photo-1471864190281-a93a3070b6de', 'photo-1585435557343-3b092031a831', 'photo-1587854692152-cbe660dbde88', 'photo-1577368211130-4bbd0181c2d6', 'photo-1631549916768-4119b2e5f926']
  },
  flower: {
    shopQ: ['florist shop', 'flower shop', 'florist storefront', 'flower market'],
    dishQ: ['flower bouquet', 'rose bouquet', 'tulips', 'birthday cake flowers', 'peony bouquet'],
    pexelsShop: [931177, 1488312, 2111170, 2253879, 4272616, 56866, 931162, 1477167, 4046563, 2111180, 4272614, 931179, 1488313, 2253874, 4046564],
    pexelsDish: [56866, 931177, 1488312, 2253879, 1477167, 2111170, 4046563, 4272616, 931162, 2253943, 2111180, 4272614, 931180, 2253874, 1488312, 1128317, 1488318, 2253879, 4046563, 4272615, 931177, 1128318, 1488317, 2253874, 56866, 931162, 1477167, 2111170, 4046563, 4272616],
    unsplashShop: ['photo-1468327768560-75b60c6f2f29', 'photo-1487530811176-3780de880c43', 'photo-1490750967868-88aa4486c946', 'photo-1457089328109-2c1c9716408b', 'photo-1464692165282-0a3d03fb1372'],
    unsplashDish: ['photo-1490750967868-88aa4486c946', 'photo-1457089328109-2c1c9716408b', 'photo-1468327768560-75b60c6f2f29', 'photo-1487530811176-3780de880c43', 'photo-1519378058450-4b2e7c0e0c3a', 'photo-1526047932273-341f2a7631f9', 'photo-1496062031456-07b8f162a322', 'photo-1464692165282-0a3d03fb1372']
  },
  tea: {
    shopQ: ['coffee shop', 'cafe interior', 'tea house', 'coffee storefront'],
    dishQ: ['latte art', 'milk tea', 'afternoon tea', 'coffee cup', 'scone', 'sandwich cafe'],
    pexelsShop: [302899, 374885, 414645, 851555, 312418, 414628, 2074130, 2396220, 1307692, 1855214, 302901, 373639, 683039, 2074131, 414646],
    pexelsDish: [302899, 312418, 374885, 414645, 851555, 414628, 2074130, 1855214, 230325, 414629, 851556, 1857684, 1998632, 302899, 312418, 374885, 414645, 683039, 1024359, 1855215, 230743, 302901, 373639, 414628, 851555, 2074130, 2396220, 312418, 374885],
    unsplashShop: ['photo-1509042239860-f550ce710b93', 'photo-1445116572660-236099ec97a0', 'photo-1495474472287-4d71bcdd2085', 'photo-1453614512568-7af4e5b5d6a3', 'photo-1554118811-1e0d58224f24'],
    unsplashDish: ['photo-1515823064-d6e0c04616a7', 'photo-1511920170033-f8396924c348', 'photo-1495474472287-4d71bcdd2085', 'photo-1509042239860-f550ce710b93', 'photo-1495474472287-4d71bcdd2085', 'photo-1486427944299-d1955d23e34d', 'photo-1551024601-bec78aea704b', 'photo-1515823064-d6e0c04616a7']
  },
  errand: {
    shopQ: ['courier office', 'delivery courier', 'post office', 'package delivery'],
    dishQ: ['parcel package', 'cardboard box', 'household items', 'delivery bag', 'shopping bags'],
    pexelsShop: [4391470, 4246120, 4246202, 4391478, 6169668, 4498135, 4391471, 4246121, 4391472, 6169669, 4246203, 4498136, 4391478, 6169670, 4246120],
    pexelsDish: [4391470, 4246120, 4246202, 4391478, 4498135, 4246121, 4391471, 6169668, 5632371, 5632397, 4099235, 3962285, 1283219, 1435904, 4393021, 4963955, 4246202, 4498135, 5632379, 5946071, 4391470, 4246120, 4099238, 3962292, 1283217, 1340116, 4391478, 6169668, 5632402, 5946083],
    unsplashShop: ['photo-1586528116311-ad8dd3c8310d', 'photo-1566576912321-d58ddd7a6088', 'photo-1607166452427-7e4477079cb9', 'photo-1616401784845-180882ba9ba8', 'photo-1592838064575-70ed626d3a0e'],
    unsplashDish: ['photo-1566576912321-d58ddd7a6088', 'photo-1586528116311-ad8dd3c8310d', 'photo-1607166452427-7e4477079cb9', 'photo-1578575437130-527eed3abbec', 'photo-1616401784845-180882ba9ba8']
  }
}

function pexelsUrl(id) {
  return `https://images.pexels.com/photos/${id}/pexels-photo-${id}.jpeg?auto=compress&cs=tinysrgb&w=800&h=600&fit=crop`
}
function unsplashUrl(id) {
  return `https://images.unsplash.com/${id}?w=800&h=600&fit=crop&q=80`
}
function isJpeg(buf) {
  return buf && buf.length > 8000 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff
}
function isPng(buf) {
  return buf && buf.length > 8000 && buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47
}
function isHtml(buf) {
  const head = buf.slice(0, 80).toString('utf8').toLowerCase()
  return head.includes('<!doctype') || head.includes('<html') || head.includes('<head')
}

async function fetchBuf(url, ua = UA) {
  const ctrl = new AbortController()
  const t = setTimeout(() => ctrl.abort(), 25000)
  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': ua, Accept: 'image/jpeg,image/png,image/*,*/*;q=0.8' },
      redirect: 'follow',
      signal: ctrl.signal
    })
    if (!res.ok) return null
    const buf = Buffer.from(await res.arrayBuffer())
    if (isHtml(buf)) return null
    if (isJpeg(buf) || isPng(buf)) return buf
    return null
  } catch {
    return null
  } finally {
    clearTimeout(t)
  }
}

async function wikiSearch(query, want) {
  const urls = []
  let offset = 0
  while (urls.length < want && offset < 120) {
    const api =
      'https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=' +
      encodeURIComponent(query + ' filetype:bitmap') +
      `&gsrnamespace=6&gsrlimit=20&gsroffset=${offset}&prop=imageinfo&iiprop=url|mime&iiurlwidth=800&format=json`
    try {
      const res = await fetch(api, { headers: { 'User-Agent': WIKI_UA } })
      if (!res.ok) break
      const json = await res.json()
      const pages = Object.values(json.query?.pages || {})
      if (!pages.length) break
      for (const p of pages) {
        const info = p.imageinfo?.[0]
        if (!info) continue
        const mime = String(info.mime || '')
        if (!/^image\/(jpeg|png)/.test(mime)) continue
        const u = info.thumburl || info.url
        if (u) urls.push(u)
      }
      offset += 20
      if (!json.continue) break
    } catch {
      break
    }
  }
  return urls
}

async function collectSources(cat, kind) {
  const cfg = CATS[cat]
  const out = []
  const seen = new Set()
  const add = (u) => {
    const key = String(u).split('?')[0]
    if (!u || seen.has(key)) return
    seen.add(key)
    out.push(u)
  }
  const ids = kind === 'shop' ? cfg.pexelsShop : cfg.pexelsDish
  const uns = kind === 'shop' ? cfg.unsplashShop : cfg.unsplashDish
  for (const id of ids) add(pexelsUrl(id))
  for (const id of uns) add(unsplashUrl(id))
  const queries = kind === 'shop' ? cfg.shopQ : cfg.dishQ
  const foundLists = await Promise.all(queries.map((q) => wikiSearch(q, kind === 'shop' ? 24 : 40)))
  foundLists.flat().forEach(add)
  return out
}

async function saveJpeg(dest, buf) {
  await mkdir(path.dirname(dest), { recursive: true })
  await writeFile(dest, buf)
}

async function fillFiles(destDir, prefix, count, urls, label) {
  await mkdir(destDir, { recursive: true })
  const needed = []
  for (let i = 1; i <= count; i++) {
    const f = path.join(destDir, `${prefix}${i}.jpg`)
    if (!(existsSync(f) && isJpeg(readFileSync(f)))) needed.push(i)
  }
  if (!needed.length) {
    console.log(`  ${label} 已齐 ${count}`)
    return count
  }
  let ui = 0
  let done = count - needed.length
  const nextJpeg = async () => {
    while (ui < urls.length) {
      const u = urls[ui++]
      const ua = String(u).includes('wikimedia') ? WIKI_UA : UA
      const buf = await fetchBuf(u, ua)
      if (buf && isJpeg(buf)) return buf
    }
    return null
  }
  await mapPool(needed, CONCURRENCY, async (i) => {
    const buf = await nextJpeg()
    if (!buf) throw new Error(`${label} 源耗尽，缺 slot ${i}`)
    await saveJpeg(path.join(destDir, `${prefix}${i}.jpg`), buf)
    done++
    process.stdout.write(`  ${label} ${done}/${count}\r`)
  })
  process.stdout.write(`  ${label} ${count}/${count}\n`)
  return count
}

async function mapPool(items, limit, fn) {
  let n = 0
  const run = async () => {
    while (n < items.length) {
      const i = n++
      await fn(items[i], i)
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, run))
}

function loadMerchants() {
  try {
    const raw = execSync(
      'docker exec ssd-mysql mysql -N -ushansuda -pshansuda ssd_account -e "SELECT user_id, category FROM merchant ORDER BY category, user_id;"',
      { encoding: 'utf8', env: { ...process.env, DOCKER_HOST: 'unix:///Users/lumengkang/.colima/default/docker.sock' } }
    )
    return raw
      .trim()
      .split('\n')
      .map((line) => line.trim().split('\t'))
      .filter((r) => r.length >= 2)
      .map(([id, cat]) => ({ id: Number(id), cat }))
  } catch (e) {
    console.warn('读商家列表失败，稍后用品类池文件。', e.message)
    return []
  }
}

async function main() {
  console.log('下载闪送达目录真实照片 →', IMG)
  const cats = Object.keys(CATS)
  for (const cat of cats) {
    console.log(`品类 ${cat}`)
    const shopUrls = await collectSources(cat, 'shop')
    const dishUrls = await collectSources(cat, 'dish')
    console.log(`  候选门头 ${shopUrls.length} 菜品 ${dishUrls.length}`)
    await fillFiles(path.join(IMG, cat), 'shop-', SHOP_N, shopUrls, `${cat}/shop`)
    await fillFiles(path.join(IMG, 'dishes', cat), '', DISH_N, dishUrls, `${cat}/dish`)
    const shop1 = path.join(IMG, cat, 'shop-1.jpg')
    const dish1 = path.join(IMG, 'dishes', cat, '1.jpg')
    await mkdir(path.join(IMG, 'cats'), { recursive: true })
    await mkdir(path.join(IMG, 'ph'), { recursive: true })
    await copyFile(shop1, path.join(IMG, 'cats', `${cat}.jpg`))
    await copyFile(dish1, path.join(IMG, 'ph', `${cat}.jpg`))
  }
  const merchants = loadMerchants()
  await mkdir(path.join(IMG, 'shops'), { recursive: true })
  const byCat = {}
  for (const m of merchants) {
    if (!byCat[m.cat]) byCat[m.cat] = []
    byCat[m.cat].push(m.id)
  }
  await Promise.all(
    Object.entries(byCat).flatMap(([cat, ids]) =>
      ids.map((id, idx) => {
        const slot = (idx % SHOP_N) + 1
        return copyFile(path.join(IMG, cat, `shop-${slot}.jpg`), path.join(IMG, 'shops', `${id}.jpg`))
      })
    )
  )
  console.log(`完成：8 品类 × ${SHOP_N} 门头 + ${DISH_N} 菜图；按店复制 ${merchants.length} 张到 images/shops/{id}.jpg`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
