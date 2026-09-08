export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function waitForMatch(loadItems, match, { tries = 10, delayMs = 400 } = {}) {
  for (let i = 0; i < tries; i++) {
    const items = await loadItems()
    const list = Array.isArray(items) ? items : []
    const hit = list.find(match)
    if (hit) return hit
    if (i < tries - 1) await sleep(delayMs)
  }
  return null
}
